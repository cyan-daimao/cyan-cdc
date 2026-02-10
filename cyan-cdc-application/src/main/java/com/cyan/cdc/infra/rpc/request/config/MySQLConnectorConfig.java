package com.cyan.cdc.infra.rpc.request.config;


import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * 数据库连接配置
 * {
 *   "name": "mysql-cdc-test",
 *   "config": {
 *     "connector.class": "io.debezium.connector.mysql.MySqlConnector",
 *     "topic.prefix":"mysql"
 *     "tasks.max": "1",
 *     "database.hostname": "你的MySQL地址",
 *     "database.port": "3306",
 *     "database.user": "用户名",
 *     "database.password": "密码",
 *     "database.server.id": "12345",
 *     "database.include.list": "要同步的库名",
 *     "table.include.list": "库名.表名1,库名.表名2",
 *     "database.history.kafka.bootstrap.servers": "kafka地址:9092",
 *     "database.history.kafka.topic": "mysql-schema-history-topic",
 *     "include.schema.changes": "true"
 *   }
 * }
 * @author cy.Y
 * @since 1.0.0
 */
@EqualsAndHashCode(callSuper = true)
@AllArgsConstructor
@NoArgsConstructor
@Data
@Accessors(chain = true)
public class MySQLConnectorConfig extends ConnectorConfig{

    /**
     * 连接器类
     */
    @JsonProperty("connector.class")
    private final String connectorClass = "io.debezium.connector.mysql.MySqlConnector";

    /**
     * Debezium 生成 Kafka Topic 的前缀（必填，避免 Topic 名称冲突）
     * 替代了旧版的 database.server.name，功能一致
     */
    @JsonProperty("topic.prefix")
    private String topicPrefix;

    /**
     * 任务最大数
     */
    @JsonProperty("tasks.max")
    private String taskMax;

    /**
     * 数据库地址
     */
    @JsonProperty("database.hostname")
    private String hostname;

    /**
     * 数据库端口
     */
    @JsonProperty("database.port")
    private String port;

    /**
     * 数据库用户名
     */
    @JsonProperty("database.user")
    private String user;

    /**
     * 数据库密码
     */
    @JsonProperty("database.password")
    private String password;

    /**
     * 这个是模拟从数据库的节点id
     * 必须唯一
     */
    @JsonProperty("database.server.id")
    private int serverId;

    /**
     * 要同步的数据库名称
     */
    @JsonProperty("database.include.list")
    private String databaseIncludeList;

    /**
     * 要同步的表名称
     */
    @JsonProperty("table.include.list")
    private String tableIncludeList;

    /**
     * kafka地址
     */
    @JsonProperty("schema.history.internal.kafka.bootstrap.servers")
    private String kafkaBootstrapServers;

    /**
     * kafka topic
     * 数据库结构变更的topic
     */
    @JsonProperty("schema.history.internal.kafka.topic")
    private String kafkaTopic;

    /**
     * 是否同步数据库结构
     */
    @JsonProperty("include.schema.changes")
    private boolean includeSchemaChanges;

    /**
     * 时间自适应，将DATETIME输出Long类型的时间戳
     */
    @JsonProperty("time.precision.mode")
    private final String timePrecisionMode = "connect";

    /**
     * 时间自适应，将DATETIME输出Long类型的时间戳
     */
    @JsonProperty("decimal.handling.mode")
    private final String decimalHandlingMode = "string";
}
