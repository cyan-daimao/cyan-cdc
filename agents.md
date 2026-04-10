# Cyan CDC 系统架构文档

## 系统概述

Cyan CDC 是一个基于 Debezium 和 Apache Flink 的分布式数据变更捕获系统，用于实时捕获 MySQL 数据库的变更数据并同步到 Apache Iceberg 表中。

## 核心组件

### 1. 配置管理服务 (CdcConfigCmdService)

**职责**：CDC 任务配置的生命周期管理

**主要功能**：
- 保存和更新 CDC 配置信息
- 启动/停止 CDC 任务
- 更新任务运行状态
- 删除 CDC 配置

**关键流程**：
- 启动任务时自动创建或更新 Debezium 连接器配置
- 新增表时自动重启连接器以获取表 Schema
- 停止任务时更新连接器的表监控列表
- 删除任务时智能判断是否需要删除连接器

**文件位置**：`cyan-cdc-application/src/main/java/com/cyan/cdc/app/service/impl/CdcConfigCmdServiceImpl.java`

---

### 2. Debezium 连接器管理 (DebeziumRPC)

**职责**：与 Debezium REST API 交互，管理连接器实例

**主要功能**：
- 创建 Debezium MySQL 连接器
- 启动/停止连接器
- 更新连接器配置
- 删除连接器
- 查询连接器状态

**关键配置**：
- 支持增量快照
- 自动捕获新增表的 Schema
- 使用 Kafka 作为消息队列

---

### 3. Flink CDC 同步任务 (CdcJob)

**职责**：从 Kafka 读取 Debezium 事件并写入 Iceberg 表

**主要功能**：
- 自动从 Debezium 消息推断表 Schema
- 创建和管理 Iceberg 表
- 解析 Debezium 事件格式
- 支持 INSERT、UPDATE、DELETE 操作
- Exactly-once 语义保证

**技术特性**：
- 使用 Flink Checkpoint 保证数据一致性
- 支持从已提交 offset 恢复
- 自动创建 Iceberg namespace 和表
- 支持多种数据类型转换

**文件位置**：`cyan-flink-sync/src/main/java/com/cyan/flink/sync/job/CdcJob.java`

---

### 4. 主应用入口 (Application)

**职责**：Spring Boot 应用启动和管理

**关键功能**：
- 服务注册与发现
- 异步任务支持
- 组件扫描

**文件位置**：`cyan-cdc-application/src/main/java/com/cyan/cdc/Application.java`

---

### 5. Flink 主程序 (Main)

**职责**：Flink 应用启动和任务编排

**主要功能**：
- 加载 YAML 配置文件
- 从 cyan-cdc-application 获取需要同步的配置列表
- 初始化 Flink 执行环境
- 创建并启动所有 CDC 同步任务

**配置项**：
- Flink 并行度
- Checkpoint 间隔
- Kafka 连接配置
- Iceberg Catalog 配置
- S3 存储配置

**文件位置**：`cyan-flink-sync/src/main/java/com/cyan/flink/sync/Main.java`

---

## 数据流转流程

```
MySQL 数据变更
    ↓
Debezium Connector (捕获变更)
    ↓
Kafka Topic (存储变更事件)
    ↓
Flink Source (消费事件)
    ↓
DebeziumEventMapper (解析事件)
    ↓
Iceberg Sink (写入目标表)
```

## 核心领域模型

### CdcConfig (CDC 配置)

**字段说明**：
- `id`: 配置唯一标识
- `name`: 数据源名称
- `datasourceType`: 数据源类型（MySQL等）
- `hostname/port`: 数据源连接地址
- `db/tbl`: 数据库和表名
- `topic`: Kafka 主题
- `username/password`: 数据源认证信息
- `runningStatus`: 运行状态（RUNNING/STOP/ERROR）
- `connectorName`: Debezium 连接器名称
- `enabled`: 是否启用监控

**文件位置**：`cyan-cdc-application/src/main/java/com/cyan/cdc/domain/CdcConfig.java`

---

## 技术栈

- **Java 21**: 核心开发语言
- **Spring Boot**: 应用框架
- **Debezium 3.4.0**: 数据变更捕获引擎
- **Apache Flink**: 流处理引擎
- **Apache Iceberg**: 数据湖存储格式
- **Apache Kafka**: 消息队列
- **MySQL**: 数据源

---

## 部署架构

### 服务组件

1. **cyan-cdc-application**: Spring Boot 应用
   - 提供 REST API 接口
   - 管理 Debezium 连接器
   - 存储 CDC 配置

2. **cyan-flink-sync**: Flink 应用
   - 执行 CDC 同步任务
   - 可独立部署多个实例
   - 支持集群模式

3. **Debezium Connect**: 连接器服务
   - 运行 MySQL 连接器
   - 捕获数据变更

4. **Kafka Cluster**: 消息中间件
   - 存储变更事件
   - 提供数据持久化

5. **Iceberg Catalog**: 元数据管理
   - REST Catalog 服务
   - 管理表元数据

---

## 关键特性

### 1. 自动 Schema 推断
系统可以从第一条 Debezium 消息中自动推断表结构，无需预先定义 Schema。

### 2. 增量快照支持
使用 Debezium 的增量快照功能，支持对新增表进行历史数据快照。

### 3. Exactly-once 语义
通过 Flink Checkpoint 机制，确保数据写入的精确一次语义。

### 4. 动态配置管理
支持动态添加/删除监控表，无需重启整个系统。

### 5. 高可用设计
- Flink Checkpoint 支持故障恢复
- Kafka offset 持久化
- Debezium offset 存储

---

## 运维要点

### 启动顺序
1. MySQL 数据库
2. Kafka 集群
3. Debezium Connect 服务
4. Iceberg Catalog 服务
5. cyan-cdc-application
6. cyan-flink-sync

### 监控指标
- Debezium 连接器状态
- Flink 任务运行状态
- Kafka 消费延迟
- Iceberg 表快照信息
- CDC 任务运行状态

### 日志配置
- 关闭 DEBUG 级别日志以减少性能影响
- 关键操作日志记录
- 错误信息完整记录

---

## 扩展点

### 1. 支持更多数据源
当前支持 MySQL，可扩展支持：
- PostgreSQL
- Oracle
- SQL Server
- MongoDB

### 2. 支持更多目标存储
当前支持 Iceberg，可扩展支持：
- Hudi
- Delta Lake
- ClickHouse
- Elasticsearch

### 3. 数据转换能力
可增加数据转换层，支持：
- 字段映射
- 数据脱敏
- 格式转换
- 数据清洗

---

## 开发指南

### 本地开发环境
1. 启动本地 MySQL、Kafka、Debezium
2. 配置 `bootstrap-dev.yml`
3. 运行 `Application.java` 启动配置管理服务
4. 配置 `config.yaml`
5. 运行 `Main.java` 启动 Flink 同步任务

### 配置文件说明
- `bootstrap-{profile}.yml`: Spring Boot 配置（不同环境）
- `config.yaml`: Flink 任务配置（Kafka、Iceberg、S3等）

---

## 作者

- **cy.Y** - 初始开发和维护

## 版本

- 当前版本: 1.0-SNAPSHOT
- Java 版本: 21
- Debezium 版本: 3.4.0.Final
