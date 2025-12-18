12.18：meta微服务的实现

# meta-service（歌曲信息微服务）

## ==端口号：8082==

## 功能范围

- 歌曲信息新增（Create）
- 歌曲信息删除（Delete：软删除，将 `deleted` 置为 `true`）
- 歌曲信息查询（Read：仅按 `song_name` 查询）
- 歌曲信息修改（Update）

## 数据库

对应库：`localhost:3306/meta_db`

表：`meta`

```sql
CREATE TABLE meta (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    song_name  VARCHAR(255) NOT NULL UNIQUE,
    artist     VARCHAR(255) NOT NULL,
    deleted    BOOLEAN DEFAULT FALSE
);
```

当前用于本地测试的连接配置写在 `meta-service/src/main/resources/application.yml`。

## 项目结构（核心代码）

根目录：`Music-Microservices/meta-service/`

```text
meta-service
├─ pom.xml
└─ src
   ├─ main
   │  ├─ java/com/zjsu/lyy/meta_service
   │  │  ├─ MetaServiceApplication.java
   │  │  ├─ controller
   │  │  │  └─ MetaController.java
   │  │  ├─ dto
   │  │  │  ├─ CreateMetaRequest.java
   │  │  │  ├─ UpdateMetaRequest.java
   │  │  │  └─ MetaResponse.java
   │  │  ├─ entity
   │  │  │  └─ Meta.java
   │  │  ├─ exception
   │  │  │  ├─ ApiError.java
   │  │  │  ├─ ConflictException.java
   │  │  │  ├─ GlobalExceptionHandler.java
   │  │  │  └─ NotFoundException.java
   │  │  ├─ repository
   │  │  │  └─ MetaRepository.java
   │  │  └─ service
   │  │     └─ MetaService.java
   │  └─ resources
   │     └─ application.yml
   └─ test
      ├─ java/com/zjsu/lyy/meta_service
      │  └─ MetaServiceApplicationTests.java
      └─ resources
         └─ application.yml
```

## API 接口

基础路径：`/api/meta`

### 1) 新增歌曲信息

- `POST /api/meta`
- Body：

```json
{
  "songName": "Shape of You",
  "artist": "Ed Sheeran"
}
```

- 返回：`201`，返回 `MetaResponse`

### 2) 按歌曲名查询

- `GET /api/meta/song-name/{songName}`
- 返回：`200`，返回 `MetaResponse`

### 3) 修改歌曲信息

- `PUT /api/meta/{id}`
- Body：

```json
{
  "songName": "Shape of You",
  "artist": "Ed Sheeran (Live)"
}
```

- 返回：`200`，返回 `MetaResponse`

### 4) 删除歌曲信息（软删除）

- `DELETE /api/meta/{id}`
- 行为：将该行的 `deleted` 置为 `true`
- 返回：`204`

## 返回与异常

- `404`：歌曲不存在
- `409`：songName 冲突
- `400`：参数校验失败

错误返回结构：

```json
{
  "timestamp": "2025-12-17T00:00:00Z",
  "status": 409,
  "message": "songName 已存在",
  "path": "/api/meta"
}
```

## 构建与运行

在 `Music-Microservices/meta-service/` 下：

- 构建与测试：mvn clean test
- 打包：mvn -DskipTests package
- 运行：java -jar .\target\meta-service-0.0.1-SNAPSHOT.jar

## 实现思路（分层）

- `controller`：只负责接收请求与参数校验，转交给 `service`
- `service`：封装业务规则（唯一性校验、软删除、DTO 转换）
- `repository`：JPA 持久层，按 `deleted=false` 查询有效数据
- `entity`：与表 `meta` 对应的实体模型（字段 `songName` 映射列 `song_name`）

