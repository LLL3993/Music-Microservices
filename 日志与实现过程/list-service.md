12.18：list微服务的实现

# list-service（收藏/歌单微服务）

## ==端口号：8083==

## 功能范围

- 用户收藏歌曲：新增、删除（硬删除）、查询（按 username）
- 用户歌单：新增、删除（硬删除）、查询（按 username / 按 playlist_name）、修改
- 用户歌单详情：新增、删除（硬删除）、查询（按 username + playlist_name）

## 数据库

对应库：`localhost:3306/list_db`

表：`favorite`、`playlist`、`playlist_detail`

```sql
CREATE TABLE favorite (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    username    VARCHAR(50) NOT NULL,
    song_name   VARCHAR(255) NOT NULL
);
```

```sql
CREATE TABLE playlist (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    playlist_name   VARCHAR(255) NOT NULL,
    username        VARCHAR(50)  NOT NULL,
    description     VARCHAR(255),
    is_public       BOOLEAN DEFAULT TRUE,
    UNIQUE KEY uk_user_playlist (username, playlist_name)
);
```

```sql
CREATE TABLE playlist_detail (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    username        VARCHAR(50)  NOT NULL,
    playlist_name   VARCHAR(255) NOT NULL,
    song_name       VARCHAR(255) NOT NULL
);
```

当前用于本地测试的连接配置写在 `list-service/src/main/resources/application.yml`。

## 与其它微服务通信（校验存在性）

- 需要 user-service：`GET /api/users/username?username=...`（不存在则返回 `404`）
- 需要 meta-service：`GET /api/meta/song-name?songName=...`（不存在则返回 `404`）
- list-service 默认开启校验：`services.validation.enabled=true`（测试环境关闭）

## 项目结构（核心代码）

根目录：`Music-Microservices/list-service/`

```text
list-service
├─ pom.xml
└─ src
   ├─ main
   │  ├─ java/com/zjsu/lyy/list_service
   │  │  ├─ ListServiceApplication.java
   │  │  ├─ controller
   │  │  │  ├─ FavoriteController.java
   │  │  │  ├─ PlaylistController.java
   │  │  │  └─ PlaylistDetailController.java
   │  │  ├─ dto
   │  │  │  ├─ CreateFavoriteRequest.java
   │  │  │  ├─ FavoriteResponse.java
   │  │  │  ├─ CreatePlaylistRequest.java
   │  │  │  ├─ UpdatePlaylistRequest.java
   │  │  │  ├─ PlaylistResponse.java
   │  │  │  ├─ CreatePlaylistDetailRequest.java
   │  │  │  └─ PlaylistDetailResponse.java
   │  │  ├─ entity
   │  │  │  ├─ Favorite.java
   │  │  │  ├─ Playlist.java
   │  │  │  └─ PlaylistDetail.java
   │  │  ├─ exception
   │  │  │  ├─ ApiError.java
   │  │  │  ├─ ConflictException.java
   │  │  │  ├─ GlobalExceptionHandler.java
   │  │  │  └─ NotFoundException.java
   │  │  ├─ integration
   │  │  │  ├─ MetaClient.java
   │  │  │  └─ UserClient.java
   │  │  ├─ repository
   │  │  │  ├─ FavoriteRepository.java
   │  │  │  ├─ PlaylistRepository.java
   │  │  │  └─ PlaylistDetailRepository.java
   │  │  └─ service
   │  │     ├─ FavoriteService.java
   │  │     ├─ PlaylistService.java
   │  │     └─ PlaylistDetailService.java
   │  └─ resources
   │     └─ application.yml
   └─ test
      ├─ java/com/zjsu/lyy/list_service
      │  └─ ListServiceApplicationTests.java
      └─ resources
         └─ application.yml
```

## API 接口

### A) 收藏（favorite）

基础路径：`/api/favorites`

1) 新增收藏

- `POST /api/favorites`
- Body：
```json
{
  "username": "tom",
  "songName": "Shape of You"
}
```
- 返回：`201`，返回 `FavoriteResponse`

2) 查询收藏（按用户名）
- `GET /api/favorites/username?username=...`
 - 返回：`200`，返回 `FavoriteResponse[]`

3) 删除收藏（硬删除）
- `DELETE /api/favorites/{id}`
- 返回：`204`

### B) 歌单（playlist）

基础路径：`/api/playlists`

1) 新增歌单
- `POST /api/playlists`
- Body：
```json
{
  "username": "tom",
  "playlistName": "我的最爱",
  "description": "常听歌曲",
  "isPublic": true
}
```
- 返回：`201`，返回 `PlaylistResponse`

2) 查询歌单列表（按 username）
- `GET /api/playlists/username?username=...`
 - 返回：`200`，返回 `PlaylistResponse[]`

3) 查询歌单列表（按 playlistName）
- `GET /api/playlists/playlist-name?playlistName=...`
 - 返回：`200`，返回 `PlaylistResponse[]`

4) 修改歌单
- `PUT /api/playlists/{id}`
- Body：
```json
{
  "playlistName": "我的最爱",
  "description": "更新后的描述",
  "isPublic": true
}
```
- 返回：`200`，返回 `PlaylistResponse`

5) 删除歌单（硬删除）
- `DELETE /api/playlists/{id}`
- 返回：`204`

### C) 歌单详情（playlist_detail）

基础路径：`/api/playlist-details`

1) 新增歌单歌曲
- `POST /api/playlist-details`
- Body：
```json
{
  "username": "tom",
  "playlistName": "我的最爱",
  "songName": "Shape of You"
}
```
- 返回：`201`，返回 `PlaylistDetailResponse`

2) 查询歌单歌曲列表
- `GET /api/playlist-details?username=...&playlistName=...`
 - 返回：`200`，返回 `PlaylistDetailResponse[]`

3) 删除歌单歌曲（硬删除）
- `DELETE /api/playlist-details/{id}`
- 返回：`204`

## 返回与异常

- `404`：用户/歌曲/歌单/详情不存在
- `409`：重复收藏/重复歌单/歌曲已在歌单中
- `400`：参数校验失败

错误返回结构：

```json
{
  "timestamp": "2025-12-18T00:00:00Z",
  "status": 409,
  "message": "歌单已存在",
  "path": "/api/playlists"
}
```

## 构建与运行

在 `Music-Microservices/list-service/` 下：

- 构建与测试：mvn clean test
- 打包：mvn -DskipTests package
- 运行：java -jar .\\target\\list-service-0.0.1-SNAPSHOT.jar

## 实现思路（分层）

- `controller`：只做参数校验与路由
- `service`：业务规则（软删除、重复校验、DTO 转换、跨服务存在性校验）
- `repository`：JPA 查询，统一以 `deleted=false` 作为有效数据条件
- `integration`：通过 HTTP 调用 user/meta 微服务完成校验
