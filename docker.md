12.20 将微服务容器化

# 容器化

## 目标

- 将 `user-service`、`meta-service`、`list-service`、`gateway-service` 以 Jar 形式打包进容器（镜像基于 `eclipse-temurin:21-jre`）
- 前端 `music-web` 由 Nginx 提供静态页面
- 仓库根目录 `data/` 不进入前端构建产物与镜像，通过挂载方式在运行时提供给前端访问
- 同一套前端资源引用方式同时支持：
  - 本地开发（`npm run dev`）
  - Docker/Nginx 部署（`docker compose up`）

## 关键设计

### 1) 后端：保留 `application.yml`，新增 `application-docker.yml`

- `application.yml` 仍用于本地直连（如 `localhost:3306`、`localhost:808x`）
- Docker 运行时通过 `SPRING_PROFILES_ACTIVE=docker` 启用 `application-docker.yml` 覆盖差异项：
  - MySQL 连接地址改为容器服务名（如 `user-mysql`）
  - 网关路由改为容器服务名（如 `http://user-service:8081`）
  - `list-service` 的下游服务 base-url 改为容器服务名

### 2) 后端：Dockerfile 只做“拷贝 Jar + 运行”

- 不在 Dockerfile 内执行 Maven/Gradle 构建
- 约定：你先在本地手动执行打包（生成 `target/*.jar`），再进行镜像构建

### 3) 前端：`/data/**` 始终是同一条访问路径

- 代码层面统一生成 `/data/music/<song>.mp3`、`/data/cover/<song>.jpg`、`/data/lrc/<song>.lrc`
- 本地开发：`music-web/vite.config.js` 的开发中间件从仓库根目录 `data/` 读取并响应 `/data/**`（不会被打包进 `dist`）
- Docker 部署：Nginx 容器把 `./data` 挂载到 `/usr/share/nginx/html/data`，直接对外提供 `/data/**`

### 4) 前端 API：默认走同源 `/api/**`，由 Nginx/Vite 代理到网关

- `music-web` 内默认 `apiBase=''`，请求变为 `/api/...`（同源）
- 本地开发：Vite proxy `/api -> http://localhost:8090`
- Docker 部署：Nginx proxy `/api -> http://gateway-service:8090`
- 如需自定义：仍可通过 `VITE_API_BASE_URL` 在构建时覆盖（例如指向远端网关）

## 已实现内容（文件清单）

### 1) 四个微服务

- `user-service/Dockerfile`
- `user-service/src/main/resources/application-docker.yml`
- `meta-service/Dockerfile`
- `meta-service/src/main/resources/application-docker.yml`
- `list-service/Dockerfile`
- `list-service/src/main/resources/application-docker.yml`
- `gateway-service/Dockerfile`
- `gateway-service/src/main/resources/application-docker.yml`

其中 `application-docker.yml` 主要变更点：

- 数据库地址改为容器名：`user-mysql` / `meta-mysql` / `list-mysql`
- `spring.jpa.hibernate.ddl-auto` 设为 `update`（用于 Docker 本地一键启动时自动建表/补表；不影响 `application.yml` 的本地策略）
- `gateway-service` 路由的 `uri` 指向容器服务名
- `list-service` 的 `services.user.base-url`、`services.meta.base-url` 指向容器服务名

### 2) 前端（Nginx）

- `music-web/Dockerfile`：只拷贝 `dist/` 到 Nginx（不在镜像里跑 `npm run build`）
- `music-web/nginx.conf`：
  - SPA 刷新回退：`try_files ... /index.html`
  - `/api/**` 反向代理到 `gateway-service:8090`
  - `/data/**` 静态文件直出（运行时由挂载提供）

### 3) 编排与数据挂载

- `docker-compose.yml`：
  - 四个微服务 + `music-web`
  - 额外包含 3 个 MySQL 容器（分别给 3 个业务微服务使用）
  - 定义数据卷 `music_data`，将宿主机 `./data` 映射为容器内的静态目录（前端不打包该目录）

## 使用方式（你手动构建 Jar / dist）

### 1) 构建后端 Jar（每个微服务各自目录）

- `user-service`：生成 `user-service/target/*.jar`
- `meta-service`：生成 `meta-service/target/*.jar`
- `list-service`：生成 `list-service/target/*.jar`
- `gateway-service`：生成 `gateway-service/target/*.jar`

### 2) 构建前端 dist（在 `music-web/`）

- 生成 `music-web/dist/`

### 3) 启动（仓库根目录）

- `docker compose up -d --build`

访问：

- 前端：`http://localhost/`
- 网关：`http://localhost:8090/`
- 静态资源（音频/封面/歌词）：`http://localhost/data/...`

## 本地开发不使用 Docker 时

- 后端继续按原方式启动（读取各自 `application.yml`）
- 前端执行 `npm run dev`：
  - `/api/**` 会被 Vite 代理到 `http://localhost:8090`
  - `/data/**` 会被 Vite 中间件映射到仓库根目录 `data/`

