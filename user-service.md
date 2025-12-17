12.17：user微服务的实现

# user-service（用户信息微服务）



## ==端口号：8081==



## 功能范围

- 用户信息新增（Create）
- 用户信息删除（Delete：软删除，将 `deleted` 置为 `true`）
- 用户信息查询（Read：仅按 `username` 查询）
- 用户信息修改（Update）

## 数据库

对应库：`localhost:3306/user_db`

表：`user`

```sql
CREATE TABLE user (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    username   VARCHAR(50)  NOT NULL UNIQUE,
    email      VARCHAR(255) NOT NULL UNIQUE,
    password   VARCHAR(255) NOT NULL,
    is_admin   BOOLEAN DEFAULT FALSE,
    deleted    BOOLEAN DEFAULT FALSE
);
```

当前用于本地测试的连接配置写在 `user-service/src/main/resources/application.yml`。

## 项目结构（核心代码）

根目录：`Music-Microservices/user-service/`

```text
user-service
├─ pom.xml
└─ src
   ├─ main
   │  ├─ java/com/zjsu/lyy/user_service
   │  │  ├─ UserServiceApplication.java
   │  │  ├─ controller
   │  │  │  └─ UserController.java
   │  │  ├─ dto
   │  │  │  ├─ CreateUserRequest.java
   │  │  │  ├─ UpdateUserRequest.java
   │  │  │  └─ UserResponse.java
   │  │  ├─ entity
   │  │  │  └─ User.java
   │  │  ├─ exception
   │  │  │  ├─ ApiError.java
   │  │  │  ├─ ConflictException.java
   │  │  │  ├─ GlobalExceptionHandler.java
   │  │  │  └─ NotFoundException.java
   │  │  ├─ repository
   │  │  │  └─ UserRepository.java
   │  │  └─ service
   │  │     └─ UserService.java
   │  └─ resources
   │     └─ application.yml
   └─ test
      ├─ java/com/zjsu/lyy/user_service
      │  └─ UserServiceApplicationTests.java
      └─ resources
         └─ application.yml
```

## API 接口

基础路径：`/api/users`

### 1) 新增用户

- `POST /api/users`
- Body：

```json
{
  "username": "tom",
  "email": "tom@example.com",
  "password": "123456",
  "isAdmin": false
}
```

- 返回：`201`，返回 `UserResponse`（不返回 `password`）

### 2) 按用户名查询用户

- `GET /api/users/username/{username}`
- 返回：`200`，返回 `UserResponse`

### 3) 修改用户

- `PUT /api/users/{id}`
- Body：

```json
{
  "username": "tom",
  "email": "tom_new@example.com",
  "password": "new_password",
  "isAdmin": false
}
```

- 返回：`200`，返回 `UserResponse`

### 4) 删除用户（软删除）

- `DELETE /api/users/{id}`
- 行为：将该用户的 `deleted` 置为 `true`
- 返回：`204`

## 返回与异常

- `404`：用户不存在
- `409`：username/email 冲突
- `400`：参数校验失败

错误返回结构：

```json
{
  "timestamp": "2025-12-17T00:00:00Z",
  "status": 409,
  "message": "username 已存在",
  "path": "/api/users"
}
```

## 构建与运行

在 `Music-Microservices/user-service/` 下：

- 构建与测试：mvn clean package
- 运行：java -jar .\target\user-service-0.0.1-SNAPSHOT.jar

## 实现思路（分层）

- `controller`：只负责接收请求与参数校验，转交给 `service`
- `service`：封装业务规则（唯一性校验、软删除、DTO 转换）
- `repository`：JPA 持久层，按 `deleted=false` 查询有效用户
- `entity`：与表 `user` 对应的实体模型
