package com.cyan.cdc.app.service;

import com.cyan.cdc.domain.CdcConfig;

/**
 * Debezium 信号服务
 * 用于向源数据库发送增量快照信号
 *
 * @author cy.Y
 * @since 1.0.0
 */
public interface DebeziumSignalService {

    /**
     * 发送增量快照信号
     * 向源数据库的信号表插入一条记录，触发指定表的全量数据同步
     *
     * @param cdcConfig  CDC配置信息（包含数据库连接信息）
     * @param dataTable  要执行快照的表，格式：db.table
     * @return 是否发送成功
     */
    boolean sendIncrementalSnapshotSignal(CdcConfig cdcConfig, String dataTable);

    /**
     * 发送增量快照信号（多个表）
     *
     * @param cdcConfig   CDC配置信息
     * @param dataTables  要执行快照的表列表，格式：["db.table1", "db.table2"]
     * @return 是否发送成功
     */
    boolean sendIncrementalSnapshotSignal(CdcConfig cdcConfig, String[] dataTables);

    /**
     * 确保信号表存在
     * 如果信号表不存在，则创建它
     *
     * @param cdcConfig CDC配置信息
     * @return 是否创建成功或已存在
     */
    boolean ensureSignalTableExists(CdcConfig cdcConfig);
}
