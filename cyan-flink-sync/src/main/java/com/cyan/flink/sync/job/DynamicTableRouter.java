package com.cyan.flink.sync.job;

import com.cyan.flink.sync.config.CdcSyncConfig;
import com.cyan.flink.sync.config.CdcTableConfig;
import org.apache.flink.api.common.functions.OpenContext;
import org.apache.flink.streaming.api.functions.ProcessFunction;
import org.apache.flink.util.Collector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 动态表路由器
 * 
 * 核心功能：
 * 1. 定时从 cdc_config 表刷新配置（支持动态增删表）
 * 2. 根据 topic 路由到不同的 Iceberg 表
 * 3. 使用 Iceberg Java API 直接写入
 *
 * @author cy.Y
 * @since 1.0.0
 */
public class DynamicTableRouter extends ProcessFunction<CdcSyncJob.DebeziumRecord, Void> {

    private static final Logger log = LoggerFactory.getLogger(DynamicTableRouter.class);

    private final CdcSyncConfig config;
    
    /**
     * topic -> table config 的映射
     * 注意：初始值会在构造时设置，但反序列化后 transient 会变成 null
     */
    private transient Map<String, CdcTableConfig> tableConfigMap;
    
    /**
     * topic -> Iceberg TableSink 的缓存
     */
    private transient Map<String, IcebergTableSink> sinkCache;
    
    /**
     * 定时刷新配置的调度器
     */
    private transient ScheduledExecutorService scheduler;
    
    /**
     * 用于传递初始配置
     */
    private final Map<String, CdcTableConfig> initialConfigs;

    public DynamicTableRouter(Map<String, CdcTableConfig> initialConfigs, CdcSyncConfig config) {
        this.initialConfigs = initialConfigs;
        this.config = config;
    }

    @Override
    public void open(OpenContext openContext) throws Exception {
        super.open(openContext);
        
        // 初始化缓存
        this.sinkCache = new ConcurrentHashMap<>();
        
        // 使用初始配置（如果 transient 为 null）
        if (tableConfigMap == null) {
            if (initialConfigs != null) {
                this.tableConfigMap = new ConcurrentHashMap<>(initialConfigs);
            } else {
                // 从数据库加载
                refreshTableConfigs();
            }
        }
        
        // 启动定时刷新配置的任务（每 30 秒刷新一次）
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "cdc-config-refresh");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleAtFixedRate(
                this::refreshTableConfigs,
                30, 30, TimeUnit.SECONDS
        );
        
        log.info("DynamicTableRouter 初始化完成，当前表数量: {}", tableConfigMap.size());
    }

    @Override
    public void processElement(
            CdcSyncJob.DebeziumRecord record,
            ProcessFunction<CdcSyncJob.DebeziumRecord, Void>.Context ctx,
            Collector<Void> out) throws Exception {
        
        String topic = record.topic;
        
        // 从配置中查找表
        CdcTableConfig tableConfig = tableConfigMap.get(topic);
        if (tableConfig == null) {
            // 可能是新增的表，但配置还没刷新，尝试从数据库查一次
            CdcTableConfig loadedConfig = loadTableConfigFromDb(topic);
            if (loadedConfig != null) {
                tableConfigMap.put(topic, loadedConfig);
                log.info("发现新表配置: topic={}, table={}", topic, loadedConfig.getIcebergTableName());
                tableConfig = loadedConfig;
            } else {
                log.debug("未找到 topic {} 的表配置，跳过", topic);
                return;
            }
        }
        
        // 使用 final 变量供 lambda 使用
        final CdcTableConfig finalTableConfig = tableConfig;
        
        // 获取或创建 Sink
        IcebergTableSink sink = sinkCache.computeIfAbsent(topic, t -> 
                createIcebergSink(finalTableConfig)
        );
        
        if (sink != null) {
            log.debug("路由消息: topic={} -> Iceberg表={}, op={}", 
                    topic, finalTableConfig.getIcebergTableName(), record.op);
            sink.write(record);
        }
    }

    @Override
    public void close() throws Exception {
        super.close();
        if (scheduler != null) {
            scheduler.shutdown();
            if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        }
        // 关闭所有 Sink
        if (sinkCache != null) {
            for (IcebergTableSink sink : sinkCache.values()) {
                try {
                    sink.close();
                } catch (Exception e) {
                    log.warn("关闭 Sink 失败: {}", e.getMessage());
                }
            }
        }
        log.info("DynamicTableRouter 已关闭");
    }

    /**
     * 从 MySQL 刷新所有表配置
     */
    private void refreshTableConfigs() {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT id, connector_name, db, tbl, enabled, running_status, flink_job_id " +
                     "FROM " + config.getMysql().getCdcConfigTable() + " " +
                     "WHERE deleted_at IS NULL AND enabled = 1 AND running_status = 'RUNNING'")) {
            
            Map<String, CdcTableConfig> newConfigs = new ConcurrentHashMap<>();
            while (rs.next()) {
                CdcTableConfig ctc = new CdcTableConfig()
                        .setId(rs.getLong("id"))
                        .setConnectorName(rs.getString("connector_name"))
                        .setDb(rs.getString("db"))
                        .setTbl(rs.getString("tbl"))
                        .setEnabled(rs.getBoolean("enabled"))
                        .setRunningStatus(rs.getString("running_status"))
                        .setFlinkJobId(rs.getString("flink_job_id"));
                newConfigs.put(ctc.getKafkaTopic(), ctc);
            }
            
            // 检测新增的表
            Map<String, CdcTableConfig> oldConfigs = this.tableConfigMap;
            for (String topic : newConfigs.keySet()) {
                if (oldConfigs == null || !oldConfigs.containsKey(topic)) {
                    log.info("检测到新增表: {}", topic);
                }
            }
            
            // 检测删除的表
            if (oldConfigs != null) {
                for (String topic : oldConfigs.keySet()) {
                    if (!newConfigs.containsKey(topic)) {
                        log.info("检测到删除表: {}", topic);
                        // 关闭并移除对应的 Sink
                        IcebergTableSink sink = sinkCache.remove(topic);
                        if (sink != null) {
                            try {
                                sink.close();
                            } catch (Exception e) {
                                log.warn("关闭已删除表的 Sink 失败: {}", e.getMessage());
                            }
                        }
                    }
                }
            }
            
            tableConfigMap = newConfigs;
            
            log.info("刷新表配置完成，当前表数量: {}", tableConfigMap.size());
            
        } catch (SQLException e) {
            log.error("刷新表配置失败: {}", e.getMessage(), e);
            // 确保不为 null
            if (tableConfigMap == null) {
                tableConfigMap = new ConcurrentHashMap<>();
            }
        }
    }

    /**
     * 从数据库加载单个表的配置（用于实时发现新表）
     */
    private CdcTableConfig loadTableConfigFromDb(String topic) {
        // topic 格式: connector_name.db.tbl
        String[] parts = topic.split("\\.", 3);
        if (parts.length != 3) {
            return null;
        }
        
        String connectorName = parts[0];
        String db = parts[1];
        String tbl = parts[2];
        
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT id, connector_name, db, tbl, enabled, running_status, flink_job_id " +
                     "FROM " + config.getMysql().getCdcConfigTable() + " " +
                     "WHERE connector_name = ? AND db = ? AND tbl = ? " +
                     "AND deleted_at IS NULL AND enabled = 1 AND running_status = 'RUNNING'")) {
            
            stmt.setString(1, connectorName);
            stmt.setString(2, db);
            stmt.setString(3, tbl);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return new CdcTableConfig()
                            .setId(rs.getLong("id"))
                            .setConnectorName(rs.getString("connector_name"))
                            .setDb(rs.getString("db"))
                            .setTbl(rs.getString("tbl"))
                            .setEnabled(rs.getBoolean("enabled"))
                            .setRunningStatus(rs.getString("running_status"))
                            .setFlinkJobId(rs.getString("flink_job_id"));
                }
            }
        } catch (SQLException e) {
            log.warn("从数据库加载表配置失败: {}", e.getMessage());
        }
        return null;
    }

    private IcebergTableSink createIcebergSink(CdcTableConfig tableConfig) {
        try {
            String tableName = config.getIceberg().getCatalogName() + "." + tableConfig.getIcebergTableName();
            return new IcebergTableSink(tableName, config);
        } catch (Exception e) {
            log.error("创建 Iceberg Sink 失败: {}", e.getMessage(), e);
            return null;
        }
    }

    private Connection getConnection() throws SQLException {
        CdcSyncConfig.MysqlConfig mysql = config.getMysql();
        String url = String.format("jdbc:mysql://%s:%d/%s?useSSL=false&serverTimezone=UTC",
                mysql.getHostname(), mysql.getPort(), mysql.getDatabase());
        return DriverManager.getConnection(url, mysql.getUsername(), mysql.getPassword());
    }
}
