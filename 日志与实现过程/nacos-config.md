12.21 实现一部分nacos config配置中心的内容，热刷新还存在问题

# Nacos Config 配置中心（仅 Docker 场景启用）

## 0. 背景与目标

在 **Docker 部署场景** 下接入 Nacos Config 配置中心，并覆盖：

1. Nacos Config 集成
2. 在 Nacos 中创建配置（内容、加密）
3. 读取配置、动态刷新、共享配置
4. 命名空间与多环境隔离、配置监听与回调
5. 微服务部署策略、滚动发布
6. Docker Compose 编排、健康检查与验证

## 1. 集成方案

### 1.1 本地运行：完全不依赖 Nacos Config

四个服务的 `application.yml` 都显式关闭了 Nacos Config，并关闭了 Spring Cloud Config 的导入检查，保证本地 `.\mvnw spring-boot:run` 不会因为缺少 `spring.config.import` 报错：

- `user-service/src/main/resources/application.yml`
- `meta-service/src/main/resources/application.yml`
- `list-service/src/main/resources/application.yml`
- `gateway-service/src/main/resources/application.yml`

关键点（示例）：

```yml
spring:
  cloud:
    config:
      import-check:
        enabled: false
    nacos:
      config:
        enabled: false
        import-check:
          enabled: false
```

### 1.2 Docker 运行：仅在 `docker` profile 开启 Nacos Config

为了解决 Docker 场景下“只订阅 `<service>.yml`，不订阅 `common.yml`”的问题，并确保 Nacos Config 在应用启动早期就完成初始化，本项目采用 `bootstrap.yml` 承载 Nacos Config 配置，并在 4 个服务的 `pom.xml` 增加 `spring-cloud-starter-bootstrap` 让 `bootstrap.yml` 生效。

这样做的效果：

- Docker 下：服务启动时会从 Nacos 拉配置，日志会明确显示订阅了 `common.yml`（以及 `shared-auth.yml`），并能动态刷新
- 本地：默认关闭 Nacos Config，本地 `.\mvnw spring-boot:run` 不依赖 Nacos

文件：

- `*/src/main/resources/bootstrap.yml`
- `*/pom.xml`（新增 `org.springframework.cloud:spring-cloud-starter-bootstrap`）
- `*/src/main/resources/application-docker.yml`（保留 discovery、datasource 等 Docker 运行配置）

关键点（示例）：

```yml
spring:
  application:
    name: list-service
  cloud:
    nacos:
      config:
        enabled: false
---
spring:
  config:
    activate:
      on-profile: docker
  cloud:
    nacos:
      config:
        enabled: true
        server-addr: nacos:8848
        namespace: ${NACOS_NAMESPACE:}
        group: ${NACOS_GROUP:DEFAULT_GROUP}
        prefix: ${spring.application.name}
        file-extension: yml
        refresh-enabled: true
        shared-configs:
          - data-id: common.yml
            group: ${NACOS_GROUP:DEFAULT_GROUP}
            refresh: true
```

## 2. Nacos 中创建配置（DataId / Group / Namespace）

### 2.1 DataId 规则（本项目约定）

本项目在 `bootstrap.yml`（docker profile）显式指定：

- `prefix: ${spring.application.name}`
- `file-extension: yml`

因此每个服务默认配置 DataId 为：

- `user-service.yml`
- `meta-service.yml`
- `list-service.yml`
- `gateway-service.yml`

共享配置（所有服务都会加载）：

- `common.yml`
- `shared-auth.yml`（仅 `user-service` 和 `gateway-service` 配置为共享）

### 2.2 Group 规则

Group 使用环境变量控制：

- `NACOS_GROUP`：默认 `DEFAULT_GROUP`

Docker Compose 中已将其作为可覆盖环境变量传入（见 `docker-compose.yml`）。

### 2.3 Namespace 与多环境隔离

Namespace 使用环境变量控制：

- `NACOS_NAMESPACE`：默认空（即 Nacos 的 `public` 命名空间）

推荐做法（最简单、隔离最强）：

- 在 Nacos 控制台创建命名空间：`dev`、`prod`
- 部署 `dev` 时设置 `NACOS_NAMESPACE=<dev-namespace-id>`
- 部署 `prod` 时设置 `NACOS_NAMESPACE=<prod-namespace-id>`

这样同一个 DataId/Group 在不同 Namespace 下互不影响。

## 3. 配置内容设计（哪些放到 Nacos）

本项目把以下配置放入 Nacos：

- `list-service`：跨服务校验开关（动态刷新验证用）
- `user-service` + `gateway-service`：JWT 密钥（共享配置 + 加密验证用）

### 3.1 `common.yml`（共享、所有服务都加载）

在 Nacos 控制台新建配置：

- `DataId`: `common.yml`
- `Group`: `DEFAULT_GROUP`
- `Type`: `YAML`

内容示例：

```yml
services:
  validation:
    enabled: true
```

说明：

- 这里把 `services.validation.enabled` 放到 `common.yml`，是为了演示“共享配置 + 动态刷新”

### 3.2 `shared-auth.yml`（共享、仅 user + gateway 加载）

在 Nacos 控制台新建配置：

- `DataId`: `shared-auth.yml`
- `Group`: `DEFAULT_GROUP`
- `Type`: `YAML`

内容示例：

```yml
security:
  jwt:
    secret: ENC(这里替换为加密密文)
    issuer: music-microservices
    expiration-seconds: 86400
```

## 4. 配置加密（实现方式与操作）

本项目采用 **Jasypt** 做“配置值加密”，优点：

- 不依赖 Nacos 额外插件
- 密文直接写在 Nacos 配置里（`ENC(...)`）
- 明文密钥通过环境变量在 Docker 侧注入

依赖已添加到 4 个服务的 `pom.xml`：

- `com.github.ulisesbocchio:jasypt-spring-boot-starter:3.0.5`

Docker Compose 已注入密钥环境变量（可覆盖）：

- `JASYPT_ENCRYPTOR_PASSWORD`

### 4.1 生成 `ENC(...)` 密文（命令行）

在任意一台装了 `Java` 的机器上（通常你的服务器上会装），执行：

1) 下载 Jasypt CLI（示例使用 1.9.3）：

```bash
curl -L -o jasypt.zip https://github.com/jasypt/jasypt/releases/download/jasypt-1.9.3/jasypt-1.9.3-dist.zip
unzip jasypt.zip
```

2) 生成密文（算法使用 `PBEWITHHMACSHA512ANDAES_256`，与 starter 默认一致）：

```bash
java -cp jasypt-1.9.3/lib/jasypt-1.9.3.jar org.jasypt.intf.cli.JasyptPBEStringEncryptionCLI ^
  input="music-microservices-dev-jwt-secret-please-change-32bytes" ^
  password="change-me" ^
  algorithm="PBEWITHHMACSHA512ANDAES_256"
```

输出里会包含类似：

- `OUTPUT: xxxxxxxx`

把这个 `OUTPUT` 填到 Nacos 的 `ENC(OUTPUT)` 里即可：

```yml
security:
  jwt:
    secret: ENC(xxxxxxxx)
```

### 4.2 Docker 侧配置密钥（解密用）

在服务器的部署目录里（运行 `docker compose up -d` 的目录），建议用环境变量或 `.env`：

`.env` 示例：

```bash
JASYPT_ENCRYPTOR_PASSWORD=change-me
NACOS_NAMESPACE=
NACOS_GROUP=DEFAULT_GROUP
```

只要 `JASYPT_ENCRYPTOR_PASSWORD` 与加密时的 `password` 一致，服务启动后会自动解密 `ENC(...)`。

## 5. 读取配置、动态刷新、共享配置

### 5.1 共享配置（Shared Config）

已在配置中开启：

- 所有服务加载：`common.yml`
- `user-service` 与 `gateway-service` 额外加载：`shared-auth.yml`

对应文件：

- `user-service/src/main/resources/bootstrap.yml`
- `meta-service/src/main/resources/bootstrap.yml`
- `list-service/src/main/resources/bootstrap.yml`
- `gateway-service/src/main/resources/bootstrap.yml`

### 5.2 动态刷新（Refresh）

实现点：

1) Nacos Config 推送变更后，Spring Cloud 会触发环境变更事件
2) 需要刷新的 Bean 标注 `@RefreshScope`，才能在配置变更时重新创建

本项目使用 `@Value` 读取动态配置，并对关键 Bean 增加 `@RefreshScope`：

- `list-service`
  - `FavoriteService`：`services.validation.enabled`（`list-service/.../FavoriteService.java:24`）
  - `PlaylistService`：`services.validation.enabled`（`list-service/.../PlaylistService.java:25`）
  - `PlaylistDetailService`：`services.validation.enabled`（`list-service/.../PlaylistDetailService.java:25`）
- `user-service`
  - `JwtService`：`security.jwt.*`（`user-service/.../JwtService.java:19`）
- `gateway-service`
  - `JwtAuthGlobalFilter`：`security.jwt.*`（`gateway-service/.../JwtAuthGlobalFilter.java:26`）

### 5.3 配置监听与回调（日志）

四个服务的启动类都监听 `EnvironmentChangeEvent` 并打印变更 key：

- `user-service/.../UserServiceApplication.java:18`
- `meta-service/.../MetaServiceApplication.java:18`
- `list-service/.../ListServiceApplication.java:18`
- `gateway-service/.../GatewayServiceApplication.java:18`

当你在 Nacos 控制台修改配置并发布后，容器日志将出现类似：

```text
Nacos config changed: [services.validation.enabled]
```

## 6. Docker Compose 编排、健康检查

文件：`docker-compose.yml`

已完成：

- Nacos 启动后再启动微服务：`depends_on.nacos.condition: service_healthy`
- 四个服务增加健康检查：通过 `curl http://localhost:<port>/actuator/health`
- 配置中心相关环境变量注入：
  - `NACOS_NAMESPACE`
  - `NACOS_GROUP`
  - `JASYPT_ENCRYPTOR_PASSWORD`

另外：

- `gateway-service` 的 Docker 路由改成 `lb://<service-name>`，通过 Nacos 服务发现 + Spring Cloud LoadBalancer 负载均衡到服务实例（支持 `user-service` 双实例滚动发布）

## 7. 微服务部署策略（滚动发布）

### 7.1 `user-service` 滚动发布

项目里已存在两个实例：

- `user-service`（8081）
- `user-service-2`（8084）

并且 `gateway-service` 在 Docker 场景路由使用 `lb://user-service`，因此可以按以下方式滚动更新：

1) 更新第一个实例：

```bash
docker compose up -d --no-deps --build user-service
docker compose ps
```

2) 等它恢复健康（`healthy`）后，再更新第二个实例：

```bash
docker compose up -d --no-deps --build user-service-2
docker compose ps
```

这样更新期间，网关仍可把流量转发到健康实例，避免整体不可用。

### 7.2 其它服务（单实例）更新

`meta-service` / `list-service` / `gateway-service` 目前是单实例，`docker compose up -d --no-deps --build <service>` 会产生短暂重启窗口。

如果后续需要“完全无中断”，可以参照 `user-service-2` 的方式为其它服务增加第二实例。

## 8. 验证方式（命令行）

### 8.1 启动整套系统

在仓库根目录：

```bash
docker compose up -d --build
docker compose ps
```

预期：

- `nacos` 为 `healthy`
- 四个微服务为 `healthy`

### 8.2 验证 Nacos 配置是否能被读取（通过接口拉取）

查看某个配置（以 `common.yml` 为例）：

```bash
curl -G "http://localhost:8848/nacos/v1/cs/configs" ^
  --data-urlencode "dataId=common.yml" ^
  --data-urlencode "group=DEFAULT_GROUP" ^
  --data-urlencode "tenant=${NACOS_NAMESPACE}"
```

预期：返回你在 Nacos 控制台创建的 YAML 内容。

### 8.3 验证动态刷新（list-service 的校验开关）

目标：通过修改 `services.validation.enabled`，观察行为变化且无需重启容器。

1) 在 Nacos 控制台把 `common.yml`（或 `list-service.yml`）中开关设置为 `true` 并发布：

```yml
services:
  validation:
    enabled: true
```

2) 直接调用 `list-service`（绕过网关，方便验证）：

```bash
curl -i -X POST "http://localhost:8083/api/favorites" ^
  -H "Content-Type: application/json" ^
  -H "X-Username: user-not-exists" ^
  -d "{\"songName\":\"十年\"}"
```

预期：返回 `404`（因为会去 `user-service` 校验用户是否存在）。

3) 在 Nacos 控制台把开关改成 `false` 并发布：

```yml
services:
  validation:
    enabled: false
```

4) 观察 `list-service` 日志（看到监听回调）：

```bash
docker compose logs -f list-service
```

预期：出现类似：

```text
Nacos config changed: [services.validation.enabled]
```

5) 再次执行第 2 步的 `curl`：

预期：返回 `201`（跳过跨服务校验，不会再因为用户不存在而失败）。

### 8.4 验证共享配置 + 加密（JWT secret 热更新）

目标：`user-service` 和 `gateway-service` 共用 Nacos 中的 `shared-auth.yml`，并且 `secret` 使用 `ENC(...)` 存储，修改后无需重启即可生效。

1) 通过网关注册用户拿 token：

```bash
curl -s -X POST "http://localhost:8090/api/auth/register" ^
  -H "Content-Type: application/json" ^
  -d "{\"username\":\"tom\",\"email\":\"tom@example.com\",\"password\":\"123456\"}"
```

把返回 JSON 中的 `token` 记为 `<TOKEN_OLD>`。

2) 用旧 token 调用任意受保护接口（示例：查询收藏）：

```bash
curl -i "http://localhost:8090/api/favorites/username" ^
  -H "Authorization: Bearer <TOKEN_OLD>"
```

预期：返回 `200`。

3) 在 Nacos 控制台修改 `shared-auth.yml` 的 `security.jwt.secret`（换成另一段 `ENC(...)`）并发布。

4) 观察两个服务日志（看到监听回调）：

```bash
docker compose logs -f user-service
docker compose logs -f gateway-service
```

预期均出现：

```text
Nacos config changed: [security.jwt.secret]
```

5) 再用 `<TOKEN_OLD>` 调用一次网关：

预期：返回 `401`（网关已用新 secret 校验旧 token，失败）。

6) 重新登录/注册获取新 token（`user-service` 已用新 secret 签发）：

```bash
curl -s -X POST "http://localhost:8090/api/auth/login" ^
  -H "Content-Type: application/json" ^
  -d "{\"username\":\"tom\",\"password\":\"123456\"}"
```

把返回 JSON 中的 `token` 记为 `<TOKEN_NEW>`。

7) 用 `<TOKEN_NEW>` 再调用网关：

```bash
curl -i "http://localhost:8090/api/favorites/username" ^
  -H "Authorization: Bearer <TOKEN_NEW>"
```

预期：返回 `200`。

## 9. 关键改动文件清单（便于定位）

- `docker-compose.yml`
- `*/pom.xml`（新增 `spring-cloud-starter-alibaba-nacos-config`、`spring-cloud-starter-bootstrap`、`jasypt-spring-boot-starter`，网关额外加 `spring-cloud-starter-loadbalancer`）
- `*/src/main/resources/application.yml`（本地关闭 config）
- `*/src/main/resources/bootstrap.yml`（Docker 开启 config + 共享配置）
- `*/src/main/resources/application-docker.yml`（Docker 运行时配置：discovery、datasource 等）
- `list-service/.../FavoriteService.java`（`@RefreshScope`）
- `list-service/.../PlaylistService.java`（`@RefreshScope`）
- `list-service/.../PlaylistDetailService.java`（`@RefreshScope`）
- `user-service/.../JwtService.java`（`@RefreshScope`）
- `gateway-service/.../JwtAuthGlobalFilter.java`（`@RefreshScope`）
- `*/.../*Application.java`（配置变更监听回调：`EnvironmentChangeEvent`）





12.22 经检验，使用`bootstrap.yml`配置文件可能会导致微服务无法订阅`common.yml`等配置文件，现在将`bootstrap.yml`里面关于nacos的内容移动到`application-docker.yml`配置文件中，并且`bootstrap.yml`删除。