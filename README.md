# Java Dev App - SQL 监控系统

这是一个基于 Spring Boot + MyBatis-Plus 的 Java 应用程序，集成了完整的 SQL 执行监控和性能分析功能。

## 功能特性

- 🔍 **实时 SQL 监控**：自动记录每条 SQL 语句的执行情况
- 📊 **性能统计分析**：提供详细的执行时间统计和性能指标
- 🚨 **智能告警**：自动识别慢 SQL 和失败的 SQL 执行
- 📝 **结构化日志**：JSON 格式的日志输出，便于日志分析和监控
- 🔗 **请求追踪**：通过 requestId 关联同一请求的所有 SQL 执行

## SQL 监控日志格式

### 1. 单条 SQL 执行日志 (SQL_EXECUTION)

每条 SQL 语句执行时都会产生一条日志记录：

```json
{
  "type": "SQL_EXECUTION",
  "requestId": "6156940e-0284-481c-a833-9b7c4637663a",
  "timestamp": "2025-09-10T02:34:50.443+00:00",
  "sql": "SELECT id,name,age,email FROM user",
  "executionTime": 11,
  "success": true,
  "resultInfo": "Query result count: 1"
}
```

#### 字段说明：

| 字段名          | 类型    | 说明                                       |
| --------------- | ------- | ------------------------------------------ |
| `type`          | String  | 日志类型，固定为 "SQL_EXECUTION"           |
| `requestId`     | String  | 请求唯一标识符，用于关联同一请求的多条 SQL |
| `timestamp`     | String  | SQL 执行时间戳 (ISO 8601 格式)             |
| `sql`           | String  | 执行的 SQL 语句（已清理格式化）            |
| `executionTime` | Long    | SQL 执行耗时（毫秒）                       |
| `success`       | Boolean | SQL 执行是否成功                           |
| `resultInfo`    | String  | 执行结果信息                               |
| `errorMessage`  | String  | 错误信息（仅当 success=false 时存在）      |

#### `resultInfo` 字段说明：

- **查询操作 (SELECT)**：`"Query result count: {数量}"`
- **更新操作 (UPDATE/DELETE)**：`"Affected rows: {数量}"`
- **批量操作 (BATCH)**：`"Affected rows: {数量}"`

### 2. 请求级别 SQL 汇总日志 (SQL_SUMMARY)

每个请求结束时会产生一条汇总日志：

```json
{
  "type": "SQL_SUMMARY",
  "requestId": "6156940e-0284-481c-a833-9b7c4637663a",
  "timestamp": "2025-09-10T02:34:50.463+00:00",
  "className": "UserController",
  "methodName": "getUser",
  "requestSuccess": true,
  "totalRequestTime": 144,
  "sqlStatistics": {
    "totalCount": 2,
    "successCount": 2,
    "failedCount": 0,
    "totalExecutionTime": 27,
    "averageExecutionTime": 13,
    "minExecutionTime": 11,
    "maxExecutionTime": 16
  },
  "performance": {
    "sqlTimePercentage": 18.75,
    "averageSqlTime": 13
  },
  "sqlDetails": [
    {
      "sql": "SELECT id,name,age,email FROM user",
      "executionTime": 11,
      "success": true,
      "resultInfo": "Query result count: 1"
    },
    {
      "sql": "SELECT id,name,age,email FROM user",
      "executionTime": 16,
      "success": true,
      "resultInfo": "Query result count: 1"
    }
  ]
}
```

#### 字段说明：

| 字段名             | 类型    | 说明                            |
| ------------------ | ------- | ------------------------------- |
| `type`             | String  | 日志类型，固定为 "SQL_SUMMARY"  |
| `requestId`        | String  | 请求唯一标识符(接口唯一请求 id) |
| `timestamp`        | String  | 汇总日志生成时间戳              |
| `className`        | String  | 执行的控制器类名                |
| `methodName`       | String  | 执行的方法名                    |
| `requestSuccess`   | Boolean | 整个请求是否成功                |
| `totalRequestTime` | Long    | 总请求处理时间（毫秒）          |
| `sqlStatistics`    | Object  | SQL 执行统计信息                |
| `performance`      | Object  | 性能分析指标                    |
| `sqlDetails`       | Array   | 所有 SQL 执行的详细信息列表     |

#### `sqlStatistics` 对象字段：

| 字段名                 | 类型    | 说明                   |
| ---------------------- | ------- | ---------------------- |
| `totalCount`           | Integer | SQL 执行总次数         |
| `successCount`         | Integer | 成功执行的 SQL 数量    |
| `failedCount`          | Integer | 失败的 SQL 数量        |
| `totalExecutionTime`   | Long    | SQL 总执行时间（毫秒） |
| `averageExecutionTime` | Long    | 平均执行时间（毫秒）   |
| `minExecutionTime`     | Long    | 最短执行时间（毫秒）   |
| `maxExecutionTime`     | Long    | 最长执行时间（毫秒）   |

#### `performance` 对象字段：

| 字段名              | 类型   | 说明                             |
| ------------------- | ------ | -------------------------------- |
| `sqlTimePercentage` | Double | SQL 执行时间占总请求时间的百分比 |
| `averageSqlTime`    | Long   | 平均 SQL 执行时间（毫秒）        |

## 智能告警功能

系统会自动识别以下情况并记录警告日志：

### 1. 慢 SQL 告警

当 SQL 执行时间超过 1000ms 时：

```
WARN - Request 6156940e-0284-481c-a833-9b7c4637663a has slow SQL execution: 1200 ms
```

### 2. SQL 执行失败告警

当请求中有 SQL 执行失败时：

```
WARN - Request 6156940e-0284-481c-a833-9b7c4637663a has 2 failed SQL executions
```

## 日志配置

### 日志文件位置

- **日志文件**：`logs/application.log`
- **日志滚动**：当文件大小超过 10MB 时自动滚动
- **保留策略**：保留最近 30 个日志文件

### 相关配置项

```properties
# 日志文件配置
logging.file.name=logs/application.log
logging.logback.rollingpolicy.max-file-size=10MB
logging.logback.rollingpolicy.max-history=30

# SQL 监控日志级别
logging.level.com.gui.app.interceptor.SqlLoggingInterceptor=INFO
logging.level.com.gui.app.aspect.SqlMonitoringAspect=INFO
```

## API 接口

### 获取用户列表

```http
GET /api/user
```

**响应示例：**

```json
{
  "total": 1,
  "param": {},
  "users": [
    {
      "id": 1,
      "name": "测试用户",
      "age": 25,
      "email": "test@example.com"
    }
  ]
}
```

### 创建用户

```http
POST /api/user
Content-Type: application/json

{
  "name": "新用户",
  "age": 30,
  "email": "newuser@example.com"
}
```

## 实际日志示例

### 创建用户操作的完整 SQL 监控日志

当执行 `POST /api/user` 创建用户时，系统会记录以下日志：

#### 1. 单条 SQL 执行日志

```json
// INSERT 操作 - 准备阶段
{
  "executionTime": 1,
  "requestId": "6825ae61-dc6e-4d97-9da6-a86e5452d91c",
  "success": true,
  "type": "SQL_EXECUTION",
  "resultInfo": "org.apache.ibatis.logging.jdbc.PreparedStatementLogger@4c0aa8a4",
  "timestamp": "2025-09-10T05:59:22.571+00:00",
  "sql": "INSERT INTO user ( name, age, email ) VALUES ( ?, ?, ? )"
}

// INSERT 操作 - 执行阶段
{
  "executionTime": 6,
  "requestId": "6825ae61-dc6e-4d97-9da6-a86e5452d91c",
  "success": true,
  "type": "SQL_EXECUTION",
  "resultInfo": "Affected rows: 1",
  "timestamp": "2025-09-10T05:59:22.577+00:00",
  "sql": "INSERT INTO user ( name, age, email ) VALUES ( ?, ?, ? )"
}

// SELECT 操作 - 查询新创建的用户
{
  "executionTime": 2,
  "requestId": "6825ae61-dc6e-4d97-9da6-a86e5452d91c",
  "success": true,
  "type": "SQL_EXECUTION",
  "resultInfo": "Query result count: 1",
  "timestamp": "2025-09-10T05:59:22.581+00:00",
  "sql": "SELECT id,name,age,email FROM user WHERE id=?"
}
```

#### 2. 请求汇总日志

```json
{
  "totalRequestTime": 17,
  "performance": {
    "sqlTimePercentage": 52.94,
    "averageSqlTime": 2
  },
  "requestId": "6825ae61-dc6e-4d97-9da6-a86e5452d91c",
  "requestSuccess": true,
  "sqlDetails": [
    {
      "executionTime": 1,
      "success": true,
      "resultInfo": "org.apache.ibatis.logging.jdbc.PreparedStatementLogger@4c0aa8a4",
      "sql": "INSERT INTO user ( name, age, email ) VALUES ( ?, ?, ? )"
    },
    {
      "executionTime": 6,
      "success": true,
      "resultInfo": "Affected rows: 1",
      "sql": "INSERT INTO user ( name, age, email ) VALUES ( ?, ?, ? )"
    },
    {
      "executionTime": 2,
      "success": true,
      "resultInfo": "Query result count: 1",
      "sql": "SELECT id,name,age,email FROM user WHERE id=?"
    }
  ],
  "methodName": "createUser",
  "className": "UserController",
  "type": "SQL_SUMMARY",
  "sqlStatistics": {
    "totalExecutionTime": 9,
    "failedCount": 0,
    "averageExecutionTime": 2,
    "minExecutionTime": 0,
    "maxExecutionTime": 6,
    "successCount": 4,
    "totalCount": 4
  },
  "timestamp": "2025-09-10T05:59:22.581+00:00"
}
```

从这个示例可以看出：

- 📊 **总请求时间**: 17ms
- 🔢 **SQL 执行次数**: 4 次（MyBatis 的 prepare 和 execute 分别记录）
- ⏱️ **SQL 总耗时**: 9ms
- 📈 **SQL 时间占比**: 52.94%
- ✅ **执行结果**: 全部成功，影响 1 行，查询到 1 条记录

## 使用示例

### 1. 查看实时 SQL 监控

```bash
# 实时查看日志
tail -f logs/application.log

# 过滤 SQL 执行日志
grep "SQL_EXECUTION" logs/application.log

# 过滤 SQL 汇总日志
grep "SQL_SUMMARY" logs/application.log
```

### 2. 分析慢 SQL

```bash
# 查找慢 SQL 告警
grep "slow SQL execution" logs/application.log

# 查找执行时间超过 100ms 的 SQL
grep "executionTime.*[1-9][0-9][0-9]" logs/application.log
```

### 3. 统计 SQL 执行情况

```bash
# 统计 SQL 执行总次数
grep -c "SQL_EXECUTION" logs/application.log

# 统计失败的 SQL
grep "\"success\":false" logs/application.log
```

## 项目结构

```
java-dev-app/
├── src/main/java/com/gui/app/
│   ├── App.java                          # Spring Boot 启动类
│   ├── aspect/
│   │   ├── HttpLoggingAspect.java        # HTTP 请求日志切面
│   │   └── SqlMonitoringAspect.java      # SQL 监控汇总切面
│   ├── config/
│   │   ├── CachedBodyHttpServletRequest.java  # 请求体缓存
│   │   └── MybatisConfig.java            # MyBatis 配置
│   ├── controllers/
│   │   └── UserController.java           # 用户控制器
│   ├── entity/
│   │   └── User.java                     # 用户实体类
│   ├── interceptor/
│   │   └── SqlLoggingInterceptor.java    # SQL 执行拦截器
│   ├── mapper/
│   │   └── UserMapper.java               # 用户数据访问层
│   └── service/
│       └── UserService.java              # 用户业务逻辑层
├── src/main/resources/
│   ├── application.properties            # 应用配置
│   └── shardingdb.yaml                  # 分库分表配置（已改为标准MySQL）
├── logs/
│   └── application.log                   # 应用日志文件
├── pom.xml                              # Maven 依赖配置
└── README.md                            # 项目文档
```

## 技术架构

### 核心组件

1. **SqlLoggingInterceptor**：MyBatis 拦截器，负责捕获 SQL 执行信息
2. **SqlMonitoringAspect**：AOP 切面，负责请求级别的 SQL 统计汇总
3. **HttpLoggingAspect**：HTTP 请求日志记录切面

### 技术栈

- **Spring Boot 3.5.5**
- **MyBatis-Plus 3.5.14**
- **MySQL 8.0**
- **Lombok**
- **Jackson**

## 数据库表结构

### user 表

```sql
CREATE TABLE `user` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(30) DEFAULT NULL,
  `age` int DEFAULT NULL,
  `email` varchar(50) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4;
```

### 示例数据

```sql
INSERT INTO user (name, age, email) VALUES
('测试用户', 25, 'test@example.com'),
('新用户', 28, 'newuser@example.com');
```

## 项目启动

```bash
# 编译项目
mvn clean compile

# 启动应用
mvn spring-boot:run

# 后台启动并记录日志
mvn spring-boot:run > app.log 2>&1 &
```

## 性能影响说明

SQL 监控功能对系统性能的影响很小：

- **内存占用**：每个请求的 SQL 信息临时存储在内存中，请求结束后自动清理
- **CPU 开销**：主要是 JSON 序列化和字符串处理，开销很小
- **IO 开销**：异步日志写入，不影响业务处理性能

建议在生产环境中根据需要调整日志级别和监控范围。

## Logstash 集成

### 日志格式适配

应用程序已经配置为与 Logstash 完美集成，支持：

#### 1. 多种日志输出格式

- **JSON 格式**：所有日志以 JSON 格式输出，便于 Logstash 解析
- **分类输出**：不同类型的日志输出到不同文件
  - `logs/application.log` - 应用主日志
  - `logs/sql-monitoring.log` - SQL 监控专用日志
  - `logs/http-requests.log` - HTTP 请求日志

#### 2. Logstash 配置字段

每条日志记录包含以下标准字段：

```json
{
  "@timestamp": "2025-09-11T16:24:52.011917+08:00",
  "@version": "1",
  "message": "{...}",
  "logger_name": "com.gui.app.interceptor.SqlLoggingInterceptor",
  "thread_name": "http-nio-8000-exec-1",
  "level": "INFO",
  "level_value": 20000,
  "app_name": "java-dev-app",
  "environment": "dev",
  "log_source": "sql_monitoring",
  "log_category": "performance",
  "requestId": "3b1bcd80-3481-4d19-a677-d341fcc3ced8",
  "traceId": "3b1bcd80-3481-4d19-a677-d341fcc3ced8"
}
```

#### 3. Logstash 配置文件

项目提供了专门的 Logstash 配置文件：

- `config/logstash.conf` - 通用 Logstash 配置
- `config/sql-monitoring-logstash.conf` - SQL 监控专用配置

#### 4. Elasticsearch 索引策略

Logstash 会根据日志类型自动创建不同的索引：

| 日志类型  | 索引名称                            | 说明                       |
| --------- | ----------------------------------- | -------------------------- |
| SQL 执行  | `sql-execution-logs-YYYY.MM.dd`     | 单条 SQL 执行记录          |
| SQL 汇总  | `sql-summary-logs-YYYY.MM.dd`       | 请求级别 SQL 统计          |
| HTTP 请求 | `http-requests-YYYY.MM.dd`          | HTTP 请求和响应            |
| 慢 SQL    | `slow-sql-logs-YYYY.MM.dd`          | 执行时间超过 500ms 的 SQL  |
| 慢请求    | `slow-requests-YYYY.MM.dd`          | 响应时间超过 1000ms 的请求 |
| 错误日志  | `error-logs-YYYY.MM.dd`             | 所有错误和异常             |
| 性能监控  | `performance-monitoring-YYYY.MM.dd` | 性能相关数据               |

#### 5. 配置说明

**application.properties 中的 Logstash 配置：**

```properties
# Logstash 相关配置
logstash.destination=192.168.1.46:5000
logstash.enabled=true
spring.profiles.active=dev
```

**logback-spring.xml 中的关键配置：**

- **TCP Appender**：直接发送日志到 Logstash
- **文件 Appender**：本地文件备份，支持 Filebeat 采集
- **分类 Logger**：不同类型日志使用不同的 Appender

### 使用 Logstash 部署

#### 1. 启动 Logstash

```bash
# 使用 SQL 监控专用配置
logstash -f config/sql-monitoring-logstash.conf

# 或使用通用配置
logstash -f config/logstash.conf
```

#### 2. 环境变量配置

```bash
export ELASTICSEARCH_HOSTS="192.168.1.46:9200"
export ELASTICSEARCH_USERNAME="elastic"
export ELASTICSEARCH_PASSWORD="your_password"
```

#### 3. Kibana 可视化

推荐创建以下 Dashboard：

- **SQL 性能监控**：基于 `sql-execution-logs-*` 索引
- **请求性能分析**：基于 `http-requests-*` 索引
- **错误监控**：基于 `error-logs-*` 索引
- **慢查询分析**：基于 `slow-sql-logs-*` 索引

## 扩展功能

可以基于现有的 SQL 监控数据实现：

- 📈 **性能监控面板**：集成 Grafana 等监控工具
- 🔔 **实时告警**：集成钉钉、企业微信等告警通道
- 📊 **SQL 分析报告**：定期生成 SQL 性能分析报告
- 🎯 **慢查询优化建议**：基于执行计划提供优化建议
- 🔍 **ELK Stack 集成**：完整的日志收集、分析和可视化方案
