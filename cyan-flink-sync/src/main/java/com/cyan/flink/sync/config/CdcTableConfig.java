package com.cyan.flink.sync.config;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * CDC表配置信息
 * 对应 cyan_cdc.cdc_config 表的一条记录
 *
 * @author cy.Y
 * @since 1.0.0
 */
public class CdcTableConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键id
     */
    private Long id;

    /**
     * 数据源名称
     */
    private String name;

    /**
     * 数据源类型: MYSQL
     */
    private String datasourceType;

    /**
     * 数据源连接地址
     */
    private String hostname;

    /**
     * 数据源端口
     */
    private String port;

    /**
     * 数据源数据库名称
     */
    private String db;

    /**
     * 数据源表名称
     */
    private String tbl;

    /**
     * 数据源用户名
     */
    private String username;

    /**
     * 数据源密码
     */
    private String password;

    /**
     * 运行状态
     */
    private String runningStatus;

    /**
     * 运行信息
     */
    private String msg;

    /**
     * debezium的server-id
     */
    private Integer serverId;

    /**
     * debezium的连接器名称(唯一)
     */
    private String connectorName;

    /**
     * 是否启用（true-启用，false-禁用）
     */
    private Boolean enabled;

    /**
     * Flink Job ID
     */
    private String flinkJobId;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;

    /**
     * 删除时间
     */
    private LocalDateTime deletedAt;

    // Getters and Setters (chain style)
    public Long getId() { return id; }
    public CdcTableConfig setId(Long id) { this.id = id; return this; }

    public String getName() { return name; }
    public CdcTableConfig setName(String name) { this.name = name; return this; }

    public String getDatasourceType() { return datasourceType; }
    public CdcTableConfig setDatasourceType(String datasourceType) { this.datasourceType = datasourceType; return this; }

    public String getHostname() { return hostname; }
    public CdcTableConfig setHostname(String hostname) { this.hostname = hostname; return this; }

    public String getPort() { return port; }
    public CdcTableConfig setPort(String port) { this.port = port; return this; }

    public String getDb() { return db; }
    public CdcTableConfig setDb(String db) { this.db = db; return this; }

    public String getTbl() { return tbl; }
    public CdcTableConfig setTbl(String tbl) { this.tbl = tbl; return this; }

    public String getUsername() { return username; }
    public CdcTableConfig setUsername(String username) { this.username = username; return this; }

    public String getPassword() { return password; }
    public CdcTableConfig setPassword(String password) { this.password = password; return this; }

    public String getRunningStatus() { return runningStatus; }
    public CdcTableConfig setRunningStatus(String runningStatus) { this.runningStatus = runningStatus; return this; }

    public String getMsg() { return msg; }
    public CdcTableConfig setMsg(String msg) { this.msg = msg; return this; }

    public Integer getServerId() { return serverId; }
    public CdcTableConfig setServerId(Integer serverId) { this.serverId = serverId; return this; }

    public String getConnectorName() { return connectorName; }
    public CdcTableConfig setConnectorName(String connectorName) { this.connectorName = connectorName; return this; }

    public Boolean getEnabled() { return enabled; }
    public CdcTableConfig setEnabled(Boolean enabled) { this.enabled = enabled; return this; }

    public String getFlinkJobId() { return flinkJobId; }
    public CdcTableConfig setFlinkJobId(String flinkJobId) { this.flinkJobId = flinkJobId; return this; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public CdcTableConfig setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; return this; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public CdcTableConfig setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; return this; }

    public LocalDateTime getDeletedAt() { return deletedAt; }
    public CdcTableConfig setDeletedAt(LocalDateTime deletedAt) { this.deletedAt = deletedAt; return this; }

    /**
     * 获取Kafka Topic名称
     * 格式: {connectorName}.{db}.{tbl}
     */
    public String getKafkaTopic() {
        return connectorName + "." + db + "." + tbl;
    }

    /**
     * 获取Iceberg表名
     */
    public String getIcebergTableName() {
        return db + "." + tbl;
    }

    /**
     * 获取唯一标识（用于任务管理）
     */
    public String getUniqueId() {
        return connectorName + "_" + db + "_" + tbl;
    }
}
