12.21 实现nacos服务注册与发现

# Nacos 接入（Docker 场景）

## 目标

- `user-service`、`meta-service`、`list-service`、`gateway-service` 在 Docker 启动时自动注册到 Nacos
- `list-service` 在 Docker 场景下不再使用硬编码地址，改为 `DiscoveryClient + RestTemplate` 按服务名调用 `user-service` 与 `meta-service`
- 暴露 `Spring Boot Actuator` 的 `/actuator/health`，用于健康检查（同时给 Nacos 侧配置健康检查路径）
- 本地开发（非 Docker）不依赖 Nacos、不需要启动 Nacos
- 确保 Nacos 先启动再启动各微服务，避免注册失败

## 实现概览

- Docker 编排新增 `nacos` 服务，并用 `healthcheck + depends_on: service_healthy` 保证启动顺序
- 4 个微服务增加：
  - Maven 依赖：`spring-cloud-starter-alibaba-nacos-discovery`、`spring-boot-starter-actuator`
  - 默认配置（`application.yml`）关闭 Nacos Discovery，保证本地启动不依赖 Nacos
  - Docker 配置（`application-docker.yml`）开启 Nacos Discovery 并指向 `nacos:8848`
  - 启动类增加 `@EnableDiscoveryClient`，明确启用注册/发现能力
- `list-service` 的 `UserClient`/`MetaClient`：
  - Docker 场景：通过 `DiscoveryClient.getInstances(serviceName)` 获取实例，再用 `RestTemplate` 调用
  - 本地场景：仍可使用 `services.*.base-url`（如 `http://localhost:8081`）直连，便于单机调试

## Docker：新增 Nacos 容器与启动顺序

文件：`docker-compose.yml`

- 新增 `nacos` 服务：
  - 镜像：`nacos/nacos-server:v2.2.3`
  - 模式：`MODE=standalone`
  - 关闭控制台鉴权：`NACOS_AUTH_ENABLE=false`
  - 端口映射：`8848:8848`
  - 健康检查：请求 `http://localhost:8848/nacos/v1/console/health`
- 四个微服务新增依赖：
  - `depends_on.nacos.condition: service_healthy`
  - 这样会在 Nacos 健康后才启动微服务，避免微服务启动时找不到注册中心

## Spring：仅 Docker Profile 启用注册

### 1) 默认（本地启动）关闭 Nacos Discovery

文件：
- `user-service/src/main/resources/application.yml`
- `meta-service/src/main/resources/application.yml`
- `list-service/src/main/resources/application.yml`
- `gateway-service/src/main/resources/application.yml`

关键点：默认设置 `spring.cloud.nacos.discovery.enabled: false`，本地不启动 Nacos 也能正常跑。

### 2) Docker Profile 开启 Nacos Discovery 并配置健康检查路径

文件：
- `*/src/main/resources/application-docker.yml`

关键点（四个服务一致）：

- `spring.cloud.nacos.discovery.enabled: true`
- `spring.cloud.nacos.discovery.server-addr: nacos:8848`
- `spring.cloud.nacos.discovery.ephemeral: false`
- `spring.cloud.nacos.discovery.health-check-path: /actuator/health`

## Actuator：暴露 /actuator/health

文件：
- `*/pom.xml`：新增 `spring-boot-starter-actuator`
- `*/src/main/resources/application-docker.yml`：暴露 `health,info`

配置点（四个服务一致）：

- `management.endpoints.web.exposure.include: health,info`
- `management.endpoint.health.probes.enabled: true`

这样在 Docker 场景下访问：

- `http://<service-host>:<port>/actuator/health`

即可得到健康状态响应。

## list-service：通过服务名调用 user/meta

### 改造点

文件：
- `list-service/src/main/java/com/zjsu/lyy/list_service/integration/UserClient.java`
- `list-service/src/main/java/com/zjsu/lyy/list_service/integration/MetaClient.java`

改造前：

- 通过 `services.user.base-url` / `services.meta.base-url`（Docker 里是 `http://user-service:8081` 这种硬编码容器名+端口）构造 HTTP Client

改造后（Docker 场景）：

- 通过 `DiscoveryClient` 获取服务实例列表：
  - `discoveryClient.getInstances("user-service")`
  - `discoveryClient.getInstances("meta-service")`
- 取到实例的 `ServiceInstance.getUri()` 后，用 `RestTemplate` 拼出具体接口 URL 调用

本地调试（非 Docker）：

- 若未启用 Discovery（默认关闭），仍会回退使用 `services.*.base-url` 直连 `localhost`，不需要 Nacos

同时：

- `list-service/src/main/resources/application-docker.yml` 删除了 `services.user.base-url` / `services.meta.base-url`，避免 Docker 场景继续依赖硬编码地址

## 验证方式（Docker）

1. 在仓库根目录启动：

- `docker compose up -d --build`

2. 访问 Nacos 控制台：

- `http://localhost:8848/nacos/`

3. 在服务列表中确认 4 个服务已注册：

- `user-service`
- `meta-service`
- `list-service`
- `gateway-service`

4. 健康检查确认：

- `http://localhost:8081/actuator/health`
- `http://localhost:8082/actuator/health`
- `http://localhost:8083/actuator/health`
- `http://localhost:8090/actuator/health`

## 关键文件清单

- `docker-compose.yml`
- `user-service/pom.xml`
- `meta-service/pom.xml`
- `list-service/pom.xml`
- `gateway-service/pom.xml`
- `*/src/main/resources/application.yml`
- `*/src/main/resources/application-docker.yml`
- `list-service/src/main/java/com/zjsu/lyy/list_service/integration/UserClient.java`
- `list-service/src/main/java/com/zjsu/lyy/list_service/integration/MetaClient.java`
