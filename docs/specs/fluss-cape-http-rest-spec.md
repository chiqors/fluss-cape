# Fluss CAPE HTTPCompatServer MVP REST API Spec

日期：2026-05-02

## 目标

在 `fluss-cape` 中新增一个 `HTTPCompatServer`，让客户端可以通过 HTTP 直接访问 Fluss 的基础元数据与数据读写能力。

这个服务不是单纯的 debug endpoint，也不是完整替代 HBase、Redis、Kafka、PostgreSQL 兼容协议。MVP 的目标是形成一个最小但闭环的 HTTP 数据通路：

1. 创建 database。
2. 创建 table。
3. 写 table。
4. 订阅 table 数据。
5. 读取数据并验证写入结果。

原始代码位置：

```text
/Users/bazhen/workspace/fluss-cape
```

## 命名调整

服务命名统一为：

```text
HTTPCompatServer
```

建议新增包名：

```text
src/main/java/org/gnuhpc/fluss/cape/http/
```

核心类建议：

```text
src/main/java/org/gnuhpc/fluss/cape/http/server/HTTPCompatServer.java
src/main/java/org/gnuhpc/fluss/cape/http/config/HTTPCompatConfig.java
src/main/java/org/gnuhpc/fluss/cape/http/service/HTTPCompatService.java
src/main/java/org/gnuhpc/fluss/cape/http/codec/FlussJsonCodec.java
src/main/java/org/gnuhpc/fluss/cape/http/subscription/HTTPSubscriptionManager.java
```

命名说明：

- `HTTPCompatServer` 与现有 `HBaseCompatServer`、`RedisCompatServer`、`KafkaCompatServer`、`PgCompatServer` 保持一致。
- 不再使用 `HttpApiServer`、`HTTP API Debug Protocol` 这类偏 debug 的命名。
- 对外协议仍然是 HTTP + JSON，但工程语义是一个 compatibility server。

## REST 资源语义修正

旧设计中曾出现：

```http
GET /api/v1/tables/{db}/{table}
```

这个路径不够符合 REST 资源层级。`table` 是 `database` 下的子资源，严格一些应该写成：

```http
GET /api/v1/databases/{db}/tables/{table}
```

判断：你的理解是正确的。旧设计更接近 Fluss `TablePath(database, table)` 的工程快捷表达，而不是严格的 REST 资源建模。新版 API 必须遵守下面的资源层级：

```text
/api/v1/databases
/api/v1/databases/{db}
/api/v1/databases/{db}/tables
/api/v1/databases/{db}/tables/{table}
/api/v1/databases/{db}/tables/{table}/rows
/api/v1/databases/{db}/tables/{table}/records
/api/v1/databases/{db}/tables/{table}/subscriptions
```

设计原则：

- database 是一级资源。
- table 是 database 的子资源。
- row、record、subscription 都是 table 的子资源。
- 不再使用 `/api/v1/tables/{db}/{table}` 这种把 database 塞进 table 路径参数的形式。
- 对确实不是简单 CRUD 的能力，优先建模成资源，例如 `subscriptions`，避免随意引入 `/lookup`、`/append`、`/scan` 这种动作型路径。
- 最小入侵原则：优先通过新增模块实现 HTTP 能力，尽量不修改既有核心读写链路；只有在复用能力必须穿透现有边界时才改旧代码，并将改动控制在最小范围且保持行为兼容。

## MVP 范围

MVP 必须实现：

| 能力 | API |
|---|---|
| 健康检查 | `GET /health`、`GET /ready` |
| 列 databases | `GET /api/v1/databases` |
| 创建 database | `POST /api/v1/databases` |
| 查看 database | `GET /api/v1/databases/{db}` |
| 删除 database | `DELETE /api/v1/databases/{db}`，可选 |
| 列 tables | `GET /api/v1/databases/{db}/tables` |
| 创建 table | `POST /api/v1/databases/{db}/tables` |
| 查看 table | `GET /api/v1/databases/{db}/tables/{table}` |
| 删除 table | `DELETE /api/v1/databases/{db}/tables/{table}`，可选 |
| Primary Key table 写入 | `POST /api/v1/databases/{db}/tables/{table}/rows` |
| Primary Key table 按主键读取 | `GET /api/v1/databases/{db}/tables/{table}/rows?key.<pk>=...` |
| Primary Key table 按主键删除 | `DELETE /api/v1/databases/{db}/tables/{table}/rows?key.<pk>=...` |
| Log table 写入 | `POST /api/v1/databases/{db}/tables/{table}/records` |
| Log table 拉取读取 | `GET /api/v1/databases/{db}/tables/{table}/records` |
| Log table 订阅 | `POST /api/v1/databases/{db}/tables/{table}/subscriptions` |
| 订阅拉取 | `GET /api/v1/databases/{db}/tables/{table}/subscriptions/{subscriptionId}/records` |
| 关闭订阅 | `DELETE /api/v1/databases/{db}/tables/{table}/subscriptions/{subscriptionId}` |

MVP 暂不实现：

- SQL 查询。
- 复杂权限模型。
- 事务 API。
- 大批量导入导出。
- 跨表查询。
- PATCH 部分字段更新。
- 高可用订阅状态持久化。
- 复杂类型全量支持。

## 通用约定

Base path：

```text
/api/v1
```

Content-Type：

```http
Content-Type: application/json
```

认证：

```http
Authorization: Bearer <token>
```

鉴权占位（本阶段只预留，不实现）：

- 生产环境 Fluss 集群通常会开启鉴权能力，HTTPCompatServer 的 REST 设计需要提前预留“透传用户名/密码”的请求字段。
- MVP 第一阶段暂不实现真实鉴权校验与透传调用链；服务端可先忽略这些字段，但请求结构必须固定下来，避免后续 API 破坏性变更。
- 约定在需要访问 Fluss 的请求体中可选携带：

```json
{
  "auth": {
    "username": "fluss_user",
    "password": "fluss_password"
  }
}
```

- 字段约束（预留）：
- `auth` 可选；缺失时使用服务默认连接身份（当前行为）。
- `auth.username`、`auth.password` 为字符串，允许为空字符串但建议非空。
- 第二阶段实现鉴权时，`auth` 将用于覆盖默认连接身份或用于构建 per-request session。
- 安全要求（实现阶段）：密码不得写入日志、审计信息需脱敏。

通用成功响应：

```json
{
  "ok": true,
  "data": {}
}
```

通用错误响应：

```json
{
  "ok": false,
  "error": {
    "code": "table_not_found",
    "message": "Table not found: app.users",
    "details": {}
  }
}
```

状态码：

| HTTP 状态码 | 场景 |
|---:|---|
| 200 | 查询、写入、删除成功 |
| 201 | 创建成功 |
| 400 | 请求参数错误、JSON 类型不匹配、缺少主键 |
| 401 | 未认证 |
| 403 | 已认证但无权限，或写入/admin 开关未开启 |
| 404 | database、table、subscription 不存在 |
| 409 | 资源已存在、表类型不匹配、状态冲突 |
| 413 | 请求体超过限制 |
| 500 | 未预期异常 |
| 503 | Fluss 不可用 |

## 配置

沿用 `CapeConfig` 的配置风格，优先级仍是 JVM system property、环境变量、默认值。

建议新增：

| 配置 | 默认值 | 说明 |
|---|---|---|
| `http.compat.enabled` | `false` | 是否启动 `HTTPCompatServer` |
| `http.compat.bind.address` | `0.0.0.0` | 监听地址 |
| `http.compat.bind.port` | `18080` | 监听端口，避免与 health check 混用 |
| `http.compat.auth.token` | 空 | Bearer token；生产环境建议强制配置 |
| `http.compat.allow.admin.write` | `false` | 是否允许创建/删除 database 和 table |
| `http.compat.allow.data.write` | `false` | 是否允许写 row/record/delete |
| `http.compat.max.request.bytes` | `1048576` | 单请求 JSON body 最大大小 |
| `http.compat.default.limit` | `100` | 读取默认 limit |
| `http.compat.max.limit` | `1000` | 读取最大 limit |
| `http.compat.subscription.max.count` | `100` | 单进程最大订阅数 |
| `http.compat.subscription.idle.timeout.ms` | `600000` | 订阅空闲超时 |

MVP 为了方便本地闭环测试，可以允许在开发环境同时打开：

```text
-Dhttp.compat.enabled=true
-Dhttp.compat.allow.admin.write=true
-Dhttp.compat.allow.data.write=true
```

生产环境默认不应开启写入能力。

## Database APIs

### List Databases

```http
GET /api/v1/databases
```

响应：

```json
{
  "ok": true,
  "data": {
    "databases": ["default", "app"]
  }
}
```

### Create Database

```http
POST /api/v1/databases
```

请求：

```json
{
  "name": "app",
  "ignoreIfExists": true,
  "auth": {
    "username": "fluss_user",
    "password": "fluss_password"
  }
}
```

行为：

- 调用 `Admin.createDatabase(...)`。
- 受 `http.compat.allow.admin.write` 控制。
- `ignoreIfExists=true` 时，已存在返回 `200` 或 `201` 均可，但响应里必须标明 `created=false`。

响应：

```json
{
  "ok": true,
  "data": {
    "database": "app",
    "created": true
  }
}
```

### Get Database

```http
GET /api/v1/databases/{db}
```

响应：

```json
{
  "ok": true,
  "data": {
    "database": "app",
    "exists": true
  }
}
```

### Delete Database

```http
DELETE /api/v1/databases/{db}?ignoreIfNotExists=true
```

MVP 可选实现。实现时必须受 `http.compat.allow.admin.write` 控制。

## Table APIs

### List Tables

```http
GET /api/v1/databases/{db}/tables
```

响应：

```json
{
  "ok": true,
  "data": {
    "database": "app",
    "tables": ["users", "events"]
  }
}
```

### Create Table

```http
POST /api/v1/databases/{db}/tables
```

Primary Key table 请求：

```json
{
  "name": "users",
  "type": "primary_key",
  "ignoreIfExists": true,
  "bucketCount": 3,
  "schema": [
    { "name": "id", "type": "BIGINT", "nullable": false },
    { "name": "name", "type": "STRING", "nullable": true },
    { "name": "age", "type": "INT", "nullable": true }
  ],
  "primaryKey": ["id"],
  "properties": {},
  "auth": {
    "username": "fluss_user",
    "password": "fluss_password"
  }
}
```

Log table 请求：

```json
{
  "name": "events",
  "type": "log",
  "ignoreIfExists": true,
  "bucketCount": 3,
  "schema": [
    { "name": "event_id", "type": "STRING", "nullable": false },
    { "name": "payload", "type": "STRING", "nullable": true },
    { "name": "event_time", "type": "BIGINT", "nullable": true }
  ],
  "properties": {},
  "auth": {
    "username": "fluss_user",
    "password": "fluss_password"
  }
}
```

响应：

```json
{
  "ok": true,
  "data": {
    "database": "app",
    "table": "users",
    "type": "primary_key",
    "created": true
  }
}
```

设计约束：

- 受 `http.compat.allow.admin.write` 控制。
- `type=primary_key` 必须提供非空 `primaryKey`。
- `type=log` 不允许提供 `primaryKey`，即使 Fluss 内部可以表达更多形态，MVP 也先保持清晰边界。
- `bucketCount` 必须大于 0。
- `schema[].name` 必须精确匹配后续 JSON 字段名，不做大小写转换。

MVP 支持类型：

| Fluss 类型 | JSON 输入 |
|---|---|
| BOOLEAN | `true/false` |
| TINYINT / SMALLINT / INT / BIGINT | JSON number |
| FLOAT / DOUBLE | JSON number |
| CHAR / VARCHAR / STRING | JSON string |
| BYTES / BINARY | base64 string |
| DATE | `YYYY-MM-DD` |
| TIMESTAMP | ISO-8601 string 或 epoch millis |

MVP 暂缓类型：

- DECIMAL
- ARRAY
- MAP
- ROW
- TIME
- TIMESTAMP_LTZ

遇到暂缓类型返回：

```json
{
  "ok": false,
  "error": {
    "code": "unsupported_type",
    "message": "Unsupported type in MVP: DECIMAL"
  }
}
```

### Get Table

```http
GET /api/v1/databases/{db}/tables/{table}
```

响应：

```json
{
  "ok": true,
  "data": {
    "database": "app",
    "table": "users",
    "type": "primary_key",
    "tableId": 12,
    "bucketCount": 3,
    "primaryKey": ["id"],
    "schema": [
      { "name": "id", "type": "BIGINT", "nullable": false },
      { "name": "name", "type": "STRING", "nullable": true },
      { "name": "age", "type": "INT", "nullable": true }
    ],
    "properties": {}
  }
}
```

### Delete Table

```http
DELETE /api/v1/databases/{db}/tables/{table}?ignoreIfNotExists=true
```

MVP 可选实现。实现时必须受 `http.compat.allow.admin.write` 控制。

## Primary Key Table Row APIs

Primary Key table 使用 `rows` 子资源。

### Upsert Rows

```http
POST /api/v1/databases/{db}/tables/{table}/rows
```

请求：

```json
{
  "rows": [
    {
      "id": 1001,
      "name": "Alice",
      "age": 30
    }
  ],
  "flush": true,
  "auth": {
    "username": "fluss_user",
    "password": "fluss_password"
  }
}
```

响应：

```json
{
  "ok": true,
  "data": {
    "database": "app",
    "table": "users",
    "affectedRows": 1,
    "flushed": true
  }
}
```

行为：

- 仅允许 Primary Key table。
- 调用 `Table.newUpsert().createWriter()`。
- 每个 row 必须包含完整 primary key 字段。
- `flush` 默认 `true`，保证 curl 写入后可以立刻读取验证。
- 受 `http.compat.allow.data.write` 控制。

### Read Rows By Primary Key

```http
GET /api/v1/databases/{db}/tables/{table}/rows?key.id=1001
```

复合主键示例：

```http
GET /api/v1/databases/{db}/tables/{table}/rows?key.tenant=t1&key.id=1001
```

响应：

```json
{
  "ok": true,
  "data": {
    "database": "app",
    "table": "users",
    "found": true,
    "row": {
      "id": 1001,
      "name": "Alice",
      "age": 30
    }
  }
}
```

行为：

- 仅允许 Primary Key table。
- 查询参数必须覆盖所有 primary key 字段。
- 调用 `Table.newLookup().createLookuper()`。
- 如果未找到，返回 `200`，`found=false`。

### Delete Rows By Primary Key

```http
DELETE /api/v1/databases/{db}/tables/{table}/rows?key.id=1001&flush=true
```

响应：

```json
{
  "ok": true,
  "data": {
    "database": "app",
    "table": "users",
    "affectedRows": 1,
    "flushed": true
  }
}
```

行为：

- 仅允许 Primary Key table。
- 查询参数必须覆盖所有 primary key 字段。
- 调用 `UpsertWriter.delete(...)`。
- 受 `http.compat.allow.data.write` 控制。

## Log Table Record APIs

Log table 使用 `records` 子资源。这里的 `record` 表示一条 append-only 数据。

### Append Records

```http
POST /api/v1/databases/{db}/tables/{table}/records
```

请求：

```json
{
  "records": [
    {
      "event_id": "e-001",
      "payload": "{\"action\":\"signup\"}",
      "event_time": 1777520000000
    }
  ],
  "flush": true,
  "auth": {
    "username": "fluss_user",
    "password": "fluss_password"
  }
}
```

响应：

```json
{
  "ok": true,
  "data": {
    "database": "app",
    "table": "events",
    "affectedRecords": 1,
    "flushed": true
  }
}
```

行为：

- 仅允许 Log table。
- 调用 `Table.newAppend().createWriter()`。
- `flush` 默认 `true`。
- 受 `http.compat.allow.data.write` 控制。

### Read Records

```http
GET /api/v1/databases/{db}/tables/{table}/records?bucket=0&offset=0&limit=100&timeoutMs=3000
```

响应：

```json
{
  "ok": true,
  "data": {
    "database": "app",
    "table": "events",
    "bucket": 0,
    "startOffset": 0,
    "nextOffset": 1,
    "records": [
      {
        "offset": 0,
        "row": {
          "event_id": "e-001",
          "payload": "{\"action\":\"signup\"}",
          "event_time": 1777520000000
        }
      }
    ],
    "truncated": false
  }
}
```

行为：

- 仅允许 Log table。
- `limit` 默认 `http.compat.default.limit`，最大 `http.compat.max.limit`。
- `bucket` MVP 建议必填；如果不填，服务端可以顺序读取所有 bucket，但必须仍受 `limit` 限制。
- `offset` 默认 `0`。
- 调用 `Table.newScan().createLogScanner()`，再 `subscribe(bucket, offset)` 或使用 Fluss 当前版本等价 API。

## Subscription APIs

MVP 的订阅先做“HTTP 长轮询 cursor”，不要求 WebSocket，也不要求持久化订阅状态。服务端在内存中保存 subscription 与对应 `LogScanner`。

订阅仅面向 Log table。Primary Key table 的 changelog 订阅可以作为后续能力，不放进 MVP。

### Create Subscription

```http
POST /api/v1/databases/{db}/tables/{table}/subscriptions
```

请求：

```json
{
  "name": "debug-reader-1",
  "buckets": [0, 1, 2],
  "start": {
    "mode": "earliest"
  },
  "maxIdleMs": 600000,
  "auth": {
    "username": "fluss_user",
    "password": "fluss_password"
  }
}
```

也允许从指定 offset 开始：

```json
{
  "buckets": [0],
  "start": {
    "mode": "offset",
    "offsets": {
      "0": 10
    }
  },
  "auth": {
    "username": "fluss_user",
    "password": "fluss_password"
  }
}
```

响应：

```json
{
  "ok": true,
  "data": {
    "database": "app",
    "table": "events",
    "subscriptionId": "sub_01HX...",
    "buckets": [0, 1, 2],
    "start": {
      "mode": "earliest"
    }
  }
}
```

行为：

- 仅允许 Log table。
- 创建内存 subscription。
- `mode` MVP 支持 `earliest` 和 `offset`。
- `latest` 可选；如果 Fluss Client 当前版本易于获取 latest offset，可以实现，否则二期再做。

### Poll Subscription Records

```http
GET /api/v1/databases/{db}/tables/{table}/subscriptions/{subscriptionId}/records?limit=100&timeoutMs=30000
```

响应：

```json
{
  "ok": true,
  "data": {
    "subscriptionId": "sub_01HX...",
    "records": [
      {
        "bucket": 0,
        "offset": 10,
        "row": {
          "event_id": "e-002",
          "payload": "{\"action\":\"login\"}",
          "event_time": 1777520100000
        }
      }
    ],
    "positions": {
      "0": 11,
      "1": 0,
      "2": 0
    }
  }
}
```

行为：

- 如果短时间没有数据，最多等待 `timeoutMs` 后返回空数组。
- 每次返回后，服务端推进该 subscription 的内存 offset。
- `limit` 仍受 `http.compat.max.limit` 约束。
- 服务重启后 subscription 丢失，客户端需要重新创建。

### Delete Subscription

```http
DELETE /api/v1/databases/{db}/tables/{table}/subscriptions/{subscriptionId}
```

响应：

```json
{
  "ok": true,
  "data": {
    "subscriptionId": "sub_01HX...",
    "closed": true
  }
}
```

行为：

- 关闭对应 `LogScanner`。
- 从内存 registry 移除 subscription。

## 端到端闭环示例

### 1. 创建 Database

```bash
curl -s -X POST http://localhost:18080/api/v1/databases \
  -H 'Content-Type: application/json' \
  -d '{"name":"app","ignoreIfExists":true,"auth":{"username":"fluss_user","password":"fluss_password"}}'
```

### 2. 创建 Primary Key Table

```bash
curl -s -X POST http://localhost:18080/api/v1/databases/app/tables \
  -H 'Content-Type: application/json' \
  -d '{
    "name": "users",
    "type": "primary_key",
    "ignoreIfExists": true,
    "bucketCount": 3,
    "schema": [
      {"name":"id","type":"BIGINT","nullable":false},
      {"name":"name","type":"STRING","nullable":true}
    ],
    "primaryKey": ["id"],
    "auth": {"username":"fluss_user","password":"fluss_password"}
  }'
```

### 3. 写 Primary Key Table

```bash
curl -s -X POST http://localhost:18080/api/v1/databases/app/tables/users/rows \
  -H 'Content-Type: application/json' \
  -d '{"rows":[{"id":1001,"name":"Alice"}],"flush":true,"auth":{"username":"fluss_user","password":"fluss_password"}}'
```

### 4. 读 Primary Key Table 验证

```bash
curl -s 'http://localhost:18080/api/v1/databases/app/tables/users/rows?key.id=1001'
```

### 5. 创建 Log Table

```bash
curl -s -X POST http://localhost:18080/api/v1/databases/app/tables \
  -H 'Content-Type: application/json' \
  -d '{
    "name": "events",
    "type": "log",
    "ignoreIfExists": true,
    "bucketCount": 3,
    "schema": [
      {"name":"event_id","type":"STRING","nullable":false},
      {"name":"payload","type":"STRING","nullable":true}
    ],
    "auth": {"username":"fluss_user","password":"fluss_password"}
  }'
```

### 6. 创建订阅

```bash
curl -s -X POST http://localhost:18080/api/v1/databases/app/tables/events/subscriptions \
  -H 'Content-Type: application/json' \
  -d '{"buckets":[0,1,2],"start":{"mode":"earliest"},"auth":{"username":"fluss_user","password":"fluss_password"}}'
```

### 7. 写 Log Table

```bash
curl -s -X POST http://localhost:18080/api/v1/databases/app/tables/events/records \
  -H 'Content-Type: application/json' \
  -d '{"records":[{"event_id":"e-001","payload":"hello"}],"flush":true,"auth":{"username":"fluss_user","password":"fluss_password"}}'
```

### 8. 拉取订阅数据

```bash
curl -s 'http://localhost:18080/api/v1/databases/app/tables/events/subscriptions/{subscriptionId}/records?limit=100&timeoutMs=30000'
```

## 工程接入建议

### 启动生命周期

`HTTPCompatServer` 应作为新的 `ServerComponent` 接入 `CAPEApplication`，与 HBase、Redis、PG、Kafka server 同级。

建议在 `ProtocolServerFactory` 增加：

```java
public HTTPCompatServer createHTTPCompatServer() throws Exception {
    HTTPCompatConfig httpConfig = HTTPCompatConfig.fromCapeConfig(capeConfig);
    return new HTTPCompatServer(httpConfig, flussConnection, flussAdmin);
}
```

建议在 `CAPEApplication.startServers()` 增加：

```java
if (capeConfig.isHTTPCompatEnabled()) {
    HTTPCompatServer httpServer = serverFactory.createHTTPCompatServer();
    lifecycleManager.register(new ServerComponentAdapter(httpServer, "HTTPCompatServer"));
}
```

如果 `HTTPCompatServer` 直接实现 `ServerComponent`，则不需要 adapter。

### HTTP Server 实现选择

MVP 可以直接使用 JDK `com.sun.net.httpserver.HttpServer`，理由：

- 项目已有 `HealthCheckServer` 使用该方式。
- HTTPCompatServer 的 MVP 目标是小规模调试和闭环验证，不追求高吞吐。
- 避免引入 Spring、JAX-RS、Vert.x 等额外框架。

后续如果需要高并发、连接控制、SSE、WebSocket 或更强路由能力，再迁移到 Netty HTTP codec。

### Fluss Client 映射

| HTTP 能力 | Fluss API |
|---|---|
| list databases | `Admin.listDatabases()` |
| create database | `Admin.createDatabase(...)` |
| list tables | `Admin.listTables(database)` |
| create table | `Admin.createTable(TablePath, TableDescriptor, ignoreIfExists)` |
| get table | `Admin.getTableInfo(TablePath)` |
| PK read | `Connection.getTable(TablePath)` + `Table.newLookup().createLookuper()` |
| PK write/delete | `Connection.getTable(TablePath)` + `Table.newUpsert().createWriter()` |
| Log append | `Connection.getTable(TablePath)` + `Table.newAppend().createWriter()` |
| Log read/subscription | `Connection.getTable(TablePath)` + `Table.newScan().createLogScanner()` |

## 实现边界与风险

### 表类型必须明确

HTTPCompatServer 不能猜测所有表都支持所有操作：

- Primary Key table 支持 `rows` read/upsert/delete。
- Log table 支持 `records` append/read/subscribe。
- 对错误表类型返回 `409 table_type_mismatch`。

### 写入默认关闭

创建 database/table、写 row/record、delete row 都可能改变线上数据。默认必须关闭，必须显式配置打开。

### 订阅是 MVP 级别

MVP 订阅状态保存在进程内存：

- 服务重启后丢失。
- 不做消费组协调。
- 不保证 exactly-once。
- 不做持久 offset commit。

这足够支持 HTTP 闭环验证，但不能包装成生产级消费协议。

### JSON 类型转换是主要复杂点

必须按 Fluss table schema 做严格转换：

- 输入字段名精确匹配。
- 未知字段默认返回 `400 unknown_field`。
- 非 nullable 字段缺失返回 `400 missing_required_field`。
- 数字溢出返回 `400 numeric_overflow`。
- 暂不支持的 Fluss 类型返回 `400 unsupported_type`。

### 最小入侵实施约束

为避免对现有代码造成破坏，HTTP 兼容层实现时遵循以下约束：

- 新增优先：HTTP 路由、请求校验、鉴权、JSON 编解码、错误映射全部放在新增包（建议 `com.alibaba.fluss.cape.http.compat`）内实现。
- 适配优先：通过 adapter/facade 方式调用现有 `Admin`、`Connection`、`Table` API，不在既有核心类中混入 HTTP 语义分支。
- 必要改动最小化：确需修改存量代码时，仅允许做小范围可回退改动（例如补充 public 方法、扩展配置项、增加 hook），禁止重写核心流程。
- 兼容优先：既有功能默认行为不变；新增配置默认关闭，不影响未启用 HTTPCompatServer 的部署。
- 可审计：每个存量文件改动都必须能说明“为何无法通过新增代码实现”，并在 PR 描述中单独列出。

## 推荐实现顺序

1. 新增 `HTTPCompatConfig` 和 `CapeConfig` 配置读取（默认关闭，保证零影响接入）。
2. 新增 `HTTPCompatServer`，先实现 `/health`、`/ready`、路由、认证、统一 JSON 响应。
3. 在新增模块内实现到 Fluss 现有 API 的 adapter 层，先打通只读 metadata API（尽量不改存量代码）。
4. 实现 database 和 table metadata API。
5. 实现 create database/create table，打开最小 admin write 闭环。
6. 实现 Primary Key table `rows` upsert/read/delete。
7. 实现 Log table `records` append/read。
8. 实现内存 subscription manager 和 subscription poll API。
9. 增加端到端 curl 测试脚本，覆盖建库、建表、写入、订阅、读取，并增加“未启用 HTTPCompatServer 时行为不变”的回归检查。

## MVP 验收标准

本地启动后，以下流程必须全部通过：

1. 通过 HTTP 创建 database。
2. 通过 HTTP 创建 Primary Key table。
3. 通过 HTTP upsert 一条 row。
4. 通过 HTTP 按主键 lookup 到同一条 row。
5. 通过 HTTP 创建 Log table。
6. 通过 HTTP 创建 log subscription。
7. 通过 HTTP append 一条 record。
8. 通过 HTTP subscription poll 拉到刚写入的 record。
9. 错误表类型调用返回清晰的 `409 table_type_mismatch`。
10. 未开启写入开关时，写入和 admin write API 返回 `403`。

## 类型转换 IT 测试矩阵（评审版）

日期：2026-05-04

目标：把 HTTP JSON -> Fluss 类型转换测试从“零散补点”升级为“矩阵化覆盖”，降低遗漏风险。

### 测试范围

- 范围仅覆盖 HTTP Compat 的类型转换、字段校验、主键转换、读写回环语义。
- 不覆盖性能压测、并发一致性、跨服务事务。
- IT 运行环境默认使用可访问 Fluss 的网络（本地或 Docker 同网络）。

### 统一断言规则

- 成功路径：断言 HTTP 状态码 + `ok=true` + 核心 `data` 字段。
- 失败路径：断言 HTTP 状态码 + `ok=false` + 稳定 `error.code`。
- 对浮点类型使用 `delta` 断言，不使用字符串精确比对。
- 对时间类型做“语义一致”断言，不强依赖毫秒/格式细节（除非协议明确要求）。

### 覆盖维度（每类类型都按同一模式检查）

- `valid-min`：最小合法值
- `valid-max`：最大合法值
- `valid-normal`：常规值
- `invalid-overflow`：上溢/下溢
- `invalid-format`：格式错误
- `invalid-type`：JSON 类型错误
- `nullability`：`nullable=true/false` 下 null 行为
- `roundtrip`：写入后读取/订阅读回一致
- `pk-path`：主键 lookup/delete 的 key 类型转换

### 测试矩阵（TC 编号）

#### A. 数值类型

| TC | 类型 | 场景 | 输入示例 | 预期 |
|---|---|---|---|---|
| TC-NUM-001 | TINYINT | min/max/normal | `-128`,`127`,`0` | 200，读回一致 |
| TC-NUM-002 | TINYINT | overflow | `-129`,`128` | 400 `invalid_argument` |
| TC-NUM-003 | SMALLINT | min/max/normal | `-32768`,`32767`,`0` | 200，读回一致 |
| TC-NUM-004 | SMALLINT | overflow | `-32769`,`32768` | 400 `invalid_argument` |
| TC-NUM-005 | INTEGER | min/max/normal | `-2147483648`,`2147483647` | 200，读回一致 |
| TC-NUM-006 | INTEGER | overflow | `-2147483649`,`2147483648` | 400 `invalid_argument` |
| TC-NUM-007 | BIGINT | min/max/normal | long 边界 | 200，读回一致 |
| TC-NUM-008 | BIGINT | overflow/format | 超 long、`\"abc\"` | 400 `invalid_argument` |
| TC-NUM-009 | FLOAT/DOUBLE | normal | `1.5`,`-2.75` | 200，delta 一致 |
| TC-NUM-010 | FLOAT/DOUBLE | invalid format | `\"NaN?\"` | 400 `invalid_argument` |

#### B. 布尔与字符串

| TC | 类型 | 场景 | 输入示例 | 预期 |
|---|---|---|---|---|
| TC-BS-001 | BOOLEAN | valid | `true`,`false` | 200，读回布尔值 |
| TC-BS-002 | BOOLEAN | invalid format | `\"truthy\"` | 400 `invalid_argument` |
| TC-BS-003 | STRING/VARCHAR/CHAR | normal/empty | `\"hello\"`,`\"\"` | 200，读回一致 |
| TC-BS-004 | STRING/VARCHAR/CHAR | invalid type | `{}` 或 `[]` | 400 `invalid_argument` |

#### C. 二进制

| TC | 类型 | 场景 | 输入示例 | 预期 |
|---|---|---|---|---|
| TC-BIN-001 | BINARY | valid base64 | `\"aGVsbG8=\"` | 200，订阅读回一致 |
| TC-BIN-002 | BINARY | invalid base64 | `\"%%%NOT_BASE64%%%\"` | 400 `invalid_argument` |
| TC-BIN-003 | BINARY | empty payload | `\"\"` | 200 |
| TC-BIN-004 | BINARY | large payload | 8KB base64 | 200，读回一致 |
| TC-BIN-005 | BINARY | larger payload | 64KB base64（可选） | 200，读回一致 |

#### D. 日期与时间

| TC | 类型 | 场景 | 输入示例 | 预期 |
|---|---|---|---|---|
| TC-TM-001 | DATE | valid | `\"2026-05-04\"` | 200，读回日期语义一致 |
| TC-TM-002 | DATE | invalid format | `\"2026-99-99\"` | 400 `invalid_argument` |
| TC-TM-003 | DATE | leap day | `\"2024-02-29\"` | 200 |
| TC-TM-004 | TIMESTAMP | valid string | `\"2026-05-04T12:34:56\"` | 200，读回非 null |
| TC-TM-005 | TIMESTAMP | valid numeric | epoch 数值 | 200，读回非 null |
| TC-TM-006 | TIMESTAMP | invalid format | `\"not-a-timestamp\"` | 400 `invalid_argument` |

#### E. nullable / 字段校验

| TC | 场景 | 输入示例 | 预期 |
|---|---|---|---|
| TC-FLD-001 | unknown field | 多传 `unknown_col` | 400 `unknown_field` |
| TC-FLD-002 | missing primary key | 缺少 pk 字段 | 400 `missing_primary_key` |
| TC-FLD-003 | nullable=false + null | 非空列传 `null` | 400 `invalid_argument` |
| TC-FLD-004 | nullable=true + null | 可空列传 `null` | 200，读回 `null` |

#### F. 主键路径转换（lookup/delete）

| TC | 场景 | 输入示例 | 预期 |
|---|---|---|---|
| TC-PK-001 | lookup valid key | `?key.id=1001` | 200 |
| TC-PK-002 | lookup invalid key type | `?key.id=abc` | 400 `invalid_argument` |
| TC-PK-003 | delete valid key | `DELETE ...?key.id=1001` | 200 |
| TC-PK-004 | delete invalid key type | `DELETE ...?key.id=abc` | 400 `invalid_argument` |
| TC-PK-005 | delete missing key | `DELETE ...` | 400 `missing_primary_key` |

### 分层组织建议（对应 Java IT 类）

- `HttpCompatTypeConversionPositiveIT`：成功边界与回环。
- `HttpCompatTypeConversionNegativeIT`：错误输入与错误码。
- `HttpCompatPrimaryKeyConversionIT`：lookup/delete key 转换专项。
- `HttpCompatBinaryAndTemporalIT`：binary/date/timestamp 专项。

说明：可按当前项目实际类名合并，不强制拆 4 个类；核心是 TC 编号和覆盖矩阵不丢失。

### 通过标准（针对本矩阵）

- 所有 `must` 级 TC（除标注“可选”的条目）必须自动化并通过。
- 每个 Fluss MVP 支持类型至少有 1 个成功回环 + 1 个失败边界。
- 每个失败 case 必须校验 `error.code`，避免只校验状态码。
- 最终在 IT 报告中输出“TC 覆盖清单（已实现/待实现）”。
