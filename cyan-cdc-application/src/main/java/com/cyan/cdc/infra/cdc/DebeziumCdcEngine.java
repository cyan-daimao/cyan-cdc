package com.cyan.cdc.infra.cdc;

import com.cyan.arch.common.api.SilentException;
import io.debezium.config.Configuration;
import io.debezium.embedded.Connect;
import io.debezium.engine.DebeziumEngine;
import io.debezium.engine.RecordChangeEvent;
import io.debezium.engine.format.ChangeEventFormat;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.connect.data.Field;
import org.apache.kafka.connect.data.Struct;
import org.apache.kafka.connect.source.SourceRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Debezium 3.4.0.Final MySQL CDC Demo（修复 topic.prefix 配置问题）
 */
@Component
@Slf4j
public class DebeziumCdcEngine {

    @Value("${kafka.url}")
    private String kafkaUrl;

    // server id的范围是1-4294967295 (2^31-1) 我们从30000000开始基本不会出现冲突
    private static int SERVER_ID = 30000000;

    // debezium引擎map
    private final static Map<String, DebeziumEngine<RecordChangeEvent<SourceRecord>>> engineMap = new ConcurrentHashMap<>();
    // 执行debezium的线程池
    private final static Map<String, ExecutorService> executorMap = new ConcurrentHashMap<>();

    /**
     * 启动 cdc
     */
    public void start(String name, String hostname, String port, String user, String password, String db, String tbl) {
        log.info("开始初始化 CDC 引擎，连接 MySQL：{}，数据库：{}, 表: {}", hostname, db, tbl);
        // debezium 引擎uid
        String uid = getUid(name, db, tbl);
        if (engineMap.get(uid) != null) {
            log.info("CDC已启动，请勿重复启动");
            return;
        }
        // debezium 配置
        Configuration config = configuration(name, hostname, port, user, password, db, tbl);

        // 创建 Debezium 引擎
        DebeziumEngine<RecordChangeEvent<SourceRecord>> engine = DebeziumEngine.create(ChangeEventFormat.of(Connect.class))
                .using(config.asProperties())
                .notifying(recordChangeEvent -> {
                    try {
                        SourceRecord sourceRecord = recordChangeEvent.record();
                        log.info("收到 CDC 记录：topic={}, partition={}", sourceRecord.topic(), sourceRecord.kafkaPartition());
                        handleChangeEvent(sourceRecord, db);
                    } catch (Exception e) {
                        log.error("处理CDC数据失败", e);
                    }
                })
                .build();

        // 保存引擎
        engineMap.put(uid, engine);
        // 使用虚拟线程
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        executor.execute(engine);
        executorMap.put(uid, executor);
        log.info("CDC 引擎启动完成（虚拟线程模式），等待数据变更...");

//         注册JVM关闭钩子，确保程序退出时优雅关闭
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("JVM正在退出，关闭所有CDC引擎...");
            shutdownAllEngines();
        }));

    }

    /**
     * 停止cdc
     */
    public void shutdownCDC(String name, String db, String tbl) {
        shutdownCDC(getUid(name, db, tbl));
    }

    /**
     * 停止 cdc
     */
    private void shutdownCDC(String uid) {
        try {
            var engine = engineMap.get(uid);
            if (engine == null) {
                throw new SilentException(uid + "未启动");
            }
            engineMap.get(uid).close();
            engineMap.remove(uid);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        executorMap.get(uid).close();
        executorMap.remove(uid);
        log.info("已关闭CDC引擎：{}", uid);
    }

    /**
     * 关闭所有CDC引擎
     */
    public void shutdownAllEngines() {
        log.info("开始关闭所有CDC引擎，共 {} 个", engineMap.size());

        // 遍历所有引擎UID并关闭
        for (String uid : engineMap.keySet()) {
            shutdownCDC(uid);
        }

        engineMap.clear();
        executorMap.clear();
        log.info("所有CDC引擎已关闭");
    }


    /**
     * 配置
     *
     * @param name     数据源名称
     * @param hostname 数据库地址
     * @param port     数据库端口
     * @param user     数据库用户名
     * @param password 数据库密码
     * @param db       库名
     * @param tbl      表名
     */
    private Configuration configuration(String name, String hostname, String port, String user, String password, String db, String tbl) {
        String uid = getUid(name, db, tbl);
        String topic = "mysql_cdc_.%s.%s".formatted(db, tbl);
        // 定义 offset 存储的 topic 名称（全局配置用）
        String offsetTopic = "debezium-global-offsets";
        // 定义当前连接器专属的 offset topic（避免多实例冲突）
        String connectorOffsetTopic = "debezium-offsets-" + uid;
        SERVER_ID++;
        return Configuration.create()
                // 全局kafka
                .with("bootstrap.servers", kafkaUrl)
                // 全局offset存储topic
                .with("offset.storage.topic", offsetTopic)
                //连接器名:自定义标识，用于区分不同的CDC连接器实例
                .with("name", uid)
                //cdc连接器
                .with("connector.class", "io.debezium.connector.mysql.MySqlConnector")
                //kafka offset存储
                .with("offset.storage", "org.apache.kafka.connect.storage.KafkaOffsetBackingStore")
                //存储所需的 Kafka 地址配置
                .with("offset.storage.kafka.bootstrap.servers", kafkaUrl)
                // 当前连接器专属offset topic
                .with("offset.storage.kafka.topic", connectorOffsetTopic)
                .with("offset.storage.partitions", 1)
                .with("offset.storage.replication.factor", 1) // Offset Topic副本数
                //kafka的topic名称
                .with("topic.prefix", topic)
                // 兼容配置：保留 database.server.name（部分场景仍需要）
//                .with("database.server.name", "mysql-cdc-server")
                // 数据库 配置
                .with("database.server.id", SERVER_ID)
                .with("database.hostname", hostname)
                .with("database.port", port)
                .with("database.user", user)
                .with("database.password", password)
                // 要监控的数据库
                .with("database.include.list", db)
                // 要监控的表，多个表用逗号分隔
                .with("table.include.list", "%s.%s".formatted(db, tbl))
                // 监听 schema 变更
                .with("include.schema.changes", "true")
                // 快照模式：initial 先全量后增量
                .with("snapshot.mode", "initial")
                // 数据库的时区
                .with("database.timezone", "Asia/Shanghai")
                // 跳过无效 DDL，防止遇到不支持的ddl，导致cdc进程卡住
                .with("database.history.skip.unparseable.ddl", "true")
                // 数据库历史存储（持久化 binlog 位置）
                .with("database.history", "io.debezium.relational.history.FileDatabaseHistory")
                .with("database.history.file.filename", "dbhistory.dat")
                .with("database.history", "io.debezium.storage.kafka.history.KafkaSchemaHistory")
                // 你的 Kafka 地址
                .with("schema.history.internal.kafka.bootstrap.servers", kafkaUrl)
                // schema 历史存储的 topic 名称
                .with("schema.history.internal.kafka.topic", topic)
                .build();
    }

    /**
     * 解析并打印 CDC 变更数据
     */
    private void handleChangeEvent(SourceRecord sourceRecord, String db) {
        Object valueObj = sourceRecord.value();
        if (!(valueObj instanceof Struct value)) {
            log.warn("非结构化数据，跳过：{}", valueObj);
            return;
        }
        boolean isDDL = false;
        for (Field field : value.schema().fields()) {
//            log.info("字段名：{} | 字段类型：{} | 字段值：{}", field.name(), field.schema().type(), value.get(field));
            if ("databaseName".equals(field.name()) && !db.equals(value.get(field))) {
                return;
            }
            if ("ddl".equals(field.name())) {
                isDDL = true;
            }
        }
        if (isDDL) {
            log.info("DDL语句：{}", value.getString("ddl"));
            return;
        }
        // 操作类型：c=新增，u=更新，d=删除，r=快照
        String op = value.getString("op");
        Struct source = value.getStruct("source");
        if (op == null || source == null) {
            return;
        }

        // 解析库名、表名、时间戳
//        String db = source.getString("db");
        String table = source.getString("table");
        Long ts = source.getInt64("ts_ms");
        // 解析变更前后数据
        Struct before = value.getStruct("before");
        Struct after = value.getStruct("after");

        // 打印核心变更信息
        log.info("=====================================");
        log.info("操作时间：{}", ts);
        log.info("数据库：{} | 表名：{}", db, table);
        log.info("操作类型：{}", getOperationDesc(op));
        if (before != null) log.info("变更前数据：{}", before);
        if (after != null) log.info("变更后数据：{}", after);
        System.out.println("监控数据:" + sourceRecord);
        log.info("=====================================\n");

        return;
    }

    /**
     * 转换操作类型描述
     */
    private String getOperationDesc(String op) {
        return switch (op) {
            case "c" -> "新增（CREATE）";
            case "u" -> "更新（UPDATE）";
            case "d" -> "删除（DELETE）";
            case "r" -> "快照（SNAPSHOT）";
            default -> "未知操作（UNKNOWN）：" + op;
        };
    }


    /**
     * debezium引擎唯一标识符
     */
    private String getUid(String name, String db, String tbl) {
        return "mysql_cdc_connector_%s.%s.%s".formatted(name, db, tbl);
    }
}