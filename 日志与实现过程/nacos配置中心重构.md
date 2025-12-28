# Music-Microservices：Nacos 配置中心热刷新重构

## 1. 问题现象与根因定位

### 现象

- 在 Nacos 配置中心修改配置并发布后，微服务日志能看到“订阅到了变更”（你项目中各服务启动类里监听了 `EnvironmentChangeEvent` 并打印了 keys）。
- 但业务代码里通过 `@Value` 注入到 Bean（例如 `JwtService`、`JwtAuthGlobalFilter`、`FavoriteService` 等）的配置值没有跟着变化，表现为“订阅到了但自身配置不生效”。

### 根因（结合对比项目结论）

在 Spring Boot 3.5.x + Spring Cloud 2025.x + Spring Cloud Alibaba 2025.x 组合里，使用 `spring.config.import=optional:nacos:...` 方式加载配置时，Nacos 的配置变更推送不一定会把最新值正确写回到 Spring `Environment` 的可用 `PropertySource` 中，导致：

- 事件能触发（你能看到 keys），但环境里的属性值仍然是旧值
- `@RefreshScope` 虽然存在，但拿到的仍然是旧值

对比项目 `todo-microservices-nacos` 中，作者在迁移到 `spring.config.import` 后也遇到“配置变更后 Bean 不刷新/值不变”的问题，最终通过自定义 `NacosDynamicRefreshConfig`：直接监听 Nacos 推送、解析 YAML、手动更新 `Environment`，再触发刷新事件，来实现“真正的热刷新”。

## 2. 本次重构目标

让 `gateway-service`、`user-service`、`meta-service`、`list-service` 在 Nacos 配置中心修改配置后：

- 不重启即可生效（热刷新）
- 覆盖你项目中现有的 `@Value + @RefreshScope` 读取方式
- 支持 `common.yml / shared-auth.yml / <service>.yml` 这类多配置源

## 3. 重构实现思路

### 3.1 改动点 1：修正 config import 的 refresh 参数

你项目 `application-docker.yml` 的 `spring.config.import` 使用了 `refreshEnabled=true`，本次改为与对比项目一致、且在 Spring Config Import 语义里更通用的：

- `refresh=true`

对应文件：

- [application-docker.yml（gateway）](file:///c:/Users/LLL/Desktop/1/Music-Microservices/gateway-service/src/main/resources/application-docker.yml)
- [application-docker.yml（user）](file:///c:/Users/LLL/Desktop/1/Music-Microservices/user-service/src/main/resources/application-docker.yml)
- [application-docker.yml（meta）](file:///c:/Users/LLL/Desktop/1/Music-Microservices/meta-service/src/main/resources/application-docker.yml)
- [application-docker.yml（list）](file:///c:/Users/LLL/Desktop/1/Music-Microservices/list-service/src/main/resources/application-docker.yml)

### 3.2 改动点 2：引入“主动写回 Environment”的动态刷新机制

在每个服务内新增一个同名配置类 `NacosDynamicRefreshConfig`（仅 `docker` profile 生效）：

- 启动时读取 `spring.config.import` 中的 nacos 条目，自动识别需要监听的 DataId + Group
- 通过 `ConfigService.addListener(dataId, group, listener)` 监听 Nacos 推送
- 收到推送后：
  - 解析 YAML 为扁平化的 properties（点号形式 key）
  - 将解析结果写入一个最高优先级的 `CompositePropertySource`（名为 `nacos-dynamic`）并注入到 `Environment`
  - 发布 `EnvironmentChangeEvent(keys)`
  - 调用 `RefreshScope.refreshAll()` 强制刷新 `@RefreshScope` Bean

这样可以确保：

- 不依赖 “Nacos 自动把值更新到 Environment” 这件事是否可靠
- 只要 Nacos 能推送内容，服务就能自己把最新值写入环境并刷新 Bean
- 同时保持配置优先级：`<service>.yml` > `shared-auth.yml` > `common.yml`

对应文件：

- [NacosDynamicRefreshConfig（gateway）](file:///c:/Users/LLL/Desktop/1/Music-Microservices/gateway-service/src/main/java/com/zjsu/lyy/gateway_service/config/NacosDynamicRefreshConfig.java)
- [NacosDynamicRefreshConfig（user）](file:///c:/Users/LLL/Desktop/1/Music-Microservices/user-service/src/main/java/com/zjsu/lyy/user_service/config/NacosDynamicRefreshConfig.java)
- [NacosDynamicRefreshConfig（meta）](file:///c:/Users/LLL/Desktop/1/Music-Microservices/meta-service/src/main/java/com/zjsu/lyy/meta_service/config/NacosDynamicRefreshConfig.java)
- [NacosDynamicRefreshConfig（list）](file:///c:/Users/LLL/Desktop/1/Music-Microservices/list-service/src/main/java/com/zjsu/lyy/list_service/config/NacosDynamicRefreshConfig.java)

## 4. 新增验证接口（用于观察热刷新是否生效）

为了不暴露 `/actuator/env` 这类可能包含敏感信息的端点，本次为四个服务都新增了一个轻量接口（仅 `docker` profile 生效）：

- `GET /api/config/info`
- 返回：
  - `service`: 当前服务名
  - `message`: `app.runtime.message`（从 Nacos 读取，支持热刷新）
  - `list-service` 额外返回：`services.validation.enabled`（用于验证你原本就想刷新的配置项）

对应文件：

- [ConfigController（gateway）](file:///c:/Users/LLL/Desktop/1/Music-Microservices/gateway-service/src/main/java/com/zjsu/lyy/gateway_service/controller/ConfigController.java)
- [ConfigController（user）](file:///c:/Users/LLL/Desktop/1/Music-Microservices/user-service/src/main/java/com/zjsu/lyy/user_service/controller/ConfigController.java)
- [ConfigController（meta）](file:///c:/Users/LLL/Desktop/1/Music-Microservices/meta-service/src/main/java/com/zjsu/lyy/meta_service/controller/ConfigController.java)
- [ConfigController（list）](file:///c:/Users/LLL/Desktop/1/Music-Microservices/list-service/src/main/java/com/zjsu/lyy/list_service/controller/ConfigController.java)

## 5. 如何在 Nacos 准备配置（用于验证）

### 5.1 进入控制台

- Nacos 控制台：`http://localhost:8080`

### 5.2 建议创建的 DataId（与你项目 docker 配置匹配）

Group 默认：`DEFAULT_GROUP`（或使用 `.env / docker compose` 传入的 `NACOS_GROUP`）

- `common.yml`
- `shared-auth.yml`（gateway + user 会加载）
- `gateway-service.yml`
- `user-service.yml`
- `meta-service.yml`
- `list-service.yml`

### 5.3 用于热刷新验证的最小配置示例

在每个服务的 `<service>.yml` 中加上（任意字符串都行）：

```yml
app:
  runtime:
    message: "v1"
```

在 `list-service.yml` 再额外放一个你项目里已经在用、且标注了 `@RefreshScope` 的开关：

```yml
services:
  validation:
    enabled: true
```

发布后即可开始验证。

## 6. 验证热刷新的命令与步骤

以下命令默认在仓库根目录执行：

### 6.1 启动整套 docker（含 nacos）

```bash
docker compose up -d --build
docker compose ps
```

确认 `nacos` 和四个服务都是 `healthy`。

### 6.2 访问四个服务的配置接口（首次读取）

```bash
curl http://localhost:8090/api/config/info
curl http://localhost:8081/api/config/info
curl http://localhost:8082/api/config/info
curl http://localhost:8083/api/config/info
```

预期能看到 `message` 为 Nacos 中配置的值（如 `v1`）。

### 6.3 在 Nacos 修改配置并发布

例如把 `user-service.yml` 里的：

```yml
app:
  runtime:
    message: "v1"
```

改为：

```yml
app:
  runtime:
    message: "v2"
```

点击发布。

### 6.4 再次请求验证是否已热刷新

等待 2~5 秒后，再请求一次：

```bash
curl http://localhost:8081/api/config/info
```

预期 `message` 变为 `v2`，且无需重启容器。

同理你可以修改 `list-service.yml` 中的：

```yml
services:
  validation:
    enabled: false
```

再访问：

```bash
curl http://localhost:8083/api/config/info
```

预期 `services.validation.enabled` 也会即时变化。

### 6.5 查看日志确认“写回 Environment + 刷新 Bean”发生

可以观察到类似日志（关键字：`Applied nacos update`）：

```bash
docker compose logs -f user-service
```

在 Windows 上也可以用：

```powershell
docker compose logs user-service | findstr "Applied nacos update"
```

## 7. 变更文件清单（便于 review）

- `application-docker.yml`：将 `refreshEnabled=true` 统一改为 `refresh=true`
- 新增 `NacosDynamicRefreshConfig`：四个服务各一份（只在 docker profile 生效）
- 新增 `/api/config/info` 验证接口：四个服务各一份（只在 docker profile 生效）

