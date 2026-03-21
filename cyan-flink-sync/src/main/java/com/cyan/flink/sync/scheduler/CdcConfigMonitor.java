package com.cyan.flink.sync.scheduler;

import com.cyan.flink.sync.config.CdcSyncConfig;
import com.cyan.flink.sync.config.CdcTableConfig;
import lombok.extern.slf4j.Slf4j;

import java.sql.*;
import java.util.*;

/**
 * CDC配置监控器
 * 监控 10.0.0.2:3306/cyan_cdc.cdc_config 表
 *
 * @author cy.Y
 * @since 1.0.0
 */
@Slf4j
public class CdcConfigMonitor {

    private final CdcSyncConfig config;

    public CdcConfigMonitor(CdcSyncConfig config) {
        this.config = config;
    }

    /**
     * 查询所有启用且运行中的CDC配置
     */
    public List<CdcTableConfig> queryRunningConfigs() {
        List<CdcTableConfig> configs = new ArrayList<>();
        String sql = String.format(
                "SELECT * " +
                "FROM %s WHERE deleted_at is NULL AND running_status = 'RUNNING' AND enabled = 1",
                config.getMysql().getCdcConfigTable()
        );

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                CdcTableConfig ctc = new CdcTableConfig()
                        .setId(rs.getLong("id"))
                        .setConnectorName(rs.getString("connector_name"))
                        .setDb(rs.getString("db"))
                        .setTbl(rs.getString("tbl"))
                        .setEnabled(rs.getBoolean("enabled"))
                        .setRunningStatus(rs.getString("running_status"))
                        .setFlinkJobId(rs.getString("flink_job_id"));
                configs.add(ctc);
            }

            log.info("查询到 {} 个运行中的CDC配置", configs.size());
        } catch (SQLException e) {
            log.error("查询CDC配置失败: {}", e.getMessage(), e);
        }

        return configs;
    }

    /**
     * 按connector分组查询配置
     * 一个connector一个Flink Job
     */
    public Map<String, List<CdcTableConfig>> queryGroupByConnector() {
        List<CdcTableConfig> configs = queryRunningConfigs();
        Map<String, List<CdcTableConfig>> grouped = new HashMap<>();
        
        for (CdcTableConfig ctc : configs) {
            grouped.computeIfAbsent(ctc.getConnectorName(), k -> new ArrayList<>()).add(ctc);
        }
        
        return grouped;
    }

    /**
     * 更新Flink Job ID (同一connector下所有表共享)
     */
    public void updateFlinkJobId(String connectorName, String jobId) {
        String sql = String.format(
                "UPDATE %s SET flink_job_id = ? WHERE connector_name = ?",
                config.getMysql().getCdcConfigTable()
        );

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, jobId);
            stmt.setString(2, connectorName);
            int rows = stmt.executeUpdate();
            log.info("更新connector {} 的 {} 条记录, Flink Job ID: {}", connectorName, rows, jobId);
        } catch (SQLException e) {
            log.error("更新Flink Job ID失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 清除Flink Job ID
     */
    public void clearFlinkJobId(String connectorName) {
        String sql = String.format(
                "UPDATE %s SET flink_job_id = NULL WHERE connector_name = ?",
                config.getMysql().getCdcConfigTable()
        );

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, connectorName);
            stmt.executeUpdate();
            log.info("清除connector {} 的Flink Job ID", connectorName);
        } catch (SQLException e) {
            log.error("清除Flink Job ID失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 获取数据库连接
     */
    private Connection getConnection() throws SQLException {
        CdcSyncConfig.MysqlConfig mysql = config.getMysql();
        String url = String.format("jdbc:mysql://%s:%d/%s?useSSL=false&serverTimezone=UTC",
                mysql.getHostname(), mysql.getPort(), mysql.getDatabase());
        return DriverManager.getConnection(url, mysql.getUsername(), mysql.getPassword());
    }
}