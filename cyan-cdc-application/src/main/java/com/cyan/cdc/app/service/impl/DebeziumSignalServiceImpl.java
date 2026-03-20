package com.cyan.cdc.app.service.impl;

import com.cyan.cdc.app.service.DebeziumSignalService;
import com.cyan.cdc.domain.CdcConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Debezium 信号服务实现
 * 通过 JDBC 向 MySQL 源数据库发送增量快照信号
 *
 * @author cy.Y
 * @since 1.0.0
 */
@Service
@Slf4j
public class DebeziumSignalServiceImpl implements DebeziumSignalService {

    /**
     * 信号表名（不包含数据库前缀）
     */
    private static final String SIGNAL_TABLE_NAME = "debezium_signal";

    @Override
    public boolean sendIncrementalSnapshotSignal(CdcConfig cdcConfig, String dataTable) {
        return sendIncrementalSnapshotSignal(cdcConfig, new String[]{dataTable});
    }

    @Override
    public boolean sendIncrementalSnapshotSignal(CdcConfig cdcConfig, String[] dataTables) {
        if (dataTables == null || dataTables.length == 0) {
            log.warn("数据表列表为空，跳过发送增量快照信号");
            return false;
        }

        // 先确保信号表存在
        if (!ensureSignalTableExists(cdcConfig)) {
            log.error("无法确保信号表存在，跳过发送增量快照信号");
            return false;
        }

        // 构建信号数据
        String dataCollections = Arrays.stream(dataTables)
                .map(table -> "\"" + table + "\"")
                .collect(Collectors.joining(",", "[", "]"));

        String dataJson = String.format("{\"data-collections\": %s, \"type\": \"incremental\"}", dataCollections);

        // 生成唯一信号ID
        String signalId = "snapshot-" + UUID.randomUUID().toString().substring(0, 8);

        // 构建 INSERT SQL（使用数据库名作为前缀）
        String fullSignalTable = cdcConfig.getDb() + "." + SIGNAL_TABLE_NAME;
        String sql = String.format(
                "INSERT INTO %s (id, type, data) VALUES ('%s', 'execute-snapshot', '%s')",
                fullSignalTable, signalId, dataJson
        );

        log.info("发送增量快照信号: id={}, tables={}", signalId, Arrays.toString(dataTables));
        log.debug("SQL: {}", sql);

        try (Connection conn = getConnection(cdcConfig);
             Statement stmt = conn.createStatement()) {

            int rows = stmt.executeUpdate(sql);
            boolean success = rows > 0;

            if (success) {
                log.info("增量快照信号发送成功: id={}, tables={}", signalId, Arrays.toString(dataTables));
            } else {
                log.warn("增量快照信号发送失败: id={}", signalId);
            }

            return success;
        } catch (SQLException e) {
            log.error("发送增量快照信号失败: {}", e.getMessage(), e);
            return false;
        }
    }

    @Override
    public boolean ensureSignalTableExists(CdcConfig cdcConfig) {
        String createTableSql = String.format(
                "CREATE TABLE IF NOT EXISTS %s.%s (" +
                "  id VARCHAR(64) NOT NULL PRIMARY KEY, " +
                "  type VARCHAR(32) NOT NULL, " +
                "  data VARCHAR(2048)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4",
                cdcConfig.getDb(), SIGNAL_TABLE_NAME
        );

        try (Connection conn = getConnection(cdcConfig);
             Statement stmt = conn.createStatement()) {

            stmt.executeUpdate(createTableSql);
            log.info("信号表已确保存在: {}.{}", cdcConfig.getDb(), SIGNAL_TABLE_NAME);
            return true;
        } catch (SQLException e) {
            log.error("创建信号表失败: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * 获取数据库连接
     *
     * @param cdcConfig CDC配置
     * @return 数据库连接
     * @throws SQLException 连接异常
     */
    private Connection getConnection(CdcConfig cdcConfig) throws SQLException {
        String url = String.format("jdbc:mysql://%s:%s/%s?useSSL=false&serverTimezone=UTC",
                cdcConfig.getHostname(),
                cdcConfig.getPort(),
                cdcConfig.getDb());

        return DriverManager.getConnection(url, cdcConfig.getUsername(), cdcConfig.getPassword());
    }
}
