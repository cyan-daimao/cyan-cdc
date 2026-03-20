package com.cyan.cdc.infra.convert;

import com.cyan.arch.common.mapstruct.MapstructConvert;
import com.cyan.cdc.domain.CdcConfig;
import com.cyan.cdc.infra.dos.CdcConfigDO;
import com.cyan.cdc.infra.rpc.request.ConnectorSaveRequest;
import com.cyan.cdc.infra.rpc.request.config.MySQLConnectorConfig;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import java.util.List;
import java.util.stream.Collectors;

/**
 * @author cy.Y
 * @since 1.0.0
 */
@Mapper(uses = MapstructConvert.class)
public interface CdcConfigInfraConvert {

    CdcConfigInfraConvert INSTANCE = Mappers.getMapper(CdcConfigInfraConvert.class);

    CdcConfigDO toCdcConfigDO(CdcConfig CDCConfig);

    CdcConfig toCdcConfig(CdcConfigDO CDCConfigDO);

    /**
     * 构建连接器保存请求（支持多个表）
     *
     * @param cdcConfig          数据源基础配置
     * @param kafkaUrl           kafka地址
     * @param databaseIncludeList 数据库列表（逗号分隔）
     * @param tableIncludeList   表列表（逗号分隔，格式：db.table）
     * @return 连接器保存请求
     */
    default ConnectorSaveRequest toConnectorSaveRequest(CdcConfig cdcConfig, String kafkaUrl, 
                                                         String databaseIncludeList, String tableIncludeList) {
        MySQLConnectorConfig config = toMySQLConnectorConfig(cdcConfig, kafkaUrl, databaseIncludeList, tableIncludeList);
        return new ConnectorSaveRequest()
                .setName(cdcConfig.getConnectorName())
                .setConfig(config);
    }

    /**
     * 构建MySQL连接器配置（用于更新接口）
     * <p>
     * 注意：schema.history.internal.kafka.topic 使用实例级别的名称，
     * 确保同一MySQL实例的所有库共享同一个历史主题，避免动态添加库时出现
     * "The db history topic is missing" 错误。
     * <p>
     * snapshot.mode 设置为 when_needed，支持动态添加表时自动获取 schema。
     *
     * @param cdcConfig          数据源基础配置
     * @param kafkaUrl           kafka地址
     * @param databaseIncludeList 数据库列表（逗号分隔）
     * @param tableIncludeList   表列表（逗号分隔，格式：db.table）
     * @return MySQL连接器配置
     */
    default MySQLConnectorConfig toMySQLConnectorConfig(CdcConfig cdcConfig, String kafkaUrl,
                                                         String databaseIncludeList, String tableIncludeList) {
        // 使用实例级别的历史主题名称，与具体库无关
        // 格式：schema-history-{hostname}-{port}
        String historyTopic = "schema-history-" + cdcConfig.getHostname() + "-" + cdcConfig.getPort();

        return new MySQLConnectorConfig()
                .setTaskMax("1")
                .setHostname(cdcConfig.getHostname())
                .setPort(cdcConfig.getPort())
                .setUser(cdcConfig.getUsername())
                .setPassword(cdcConfig.getPassword())
                .setServerId(cdcConfig.getServerId())
                .setTopicPrefix(cdcConfig.getConnectorName())
                // 不设置 database.include.list，让 Debezium 读取所有数据库的 binlog
                // 只通过 table.include.list 来过滤具体的表
                .setTableIncludeList(tableIncludeList)
                .setKafkaTopic(historyTopic)
                .setKafkaBootstrapServers(kafkaUrl)
                .setIncludeSchemaChanges(true)
                // 使用 when_needed 模式，配合增量快照框架支持动态添加表
                .setSnapshotMode("when_needed")
                // 增量快照配置
                .setIncrementalSnapshotEnabled(true)
                .setIncrementalSnapshotChunkSize("1024");
    }

    /**
     * 从配置列表构建数据库列表字符串
     *
     * @param cdcConfigs cdc配置列表
     * @return 数据库列表（逗号分隔，去重）
     */
    default String buildDatabaseIncludeList(List<CdcConfig> cdcConfigs) {
        return cdcConfigs.stream()
                .map(CdcConfig::getDb)
                .distinct()
                .collect(Collectors.joining(","));
    }

    /**
     * 从配置列表构建表列表字符串
     *
     * @param cdcConfigs cdc配置列表
     * @return 表列表（逗号分隔，格式：db.table）
     */
    default String buildTableIncludeList(List<CdcConfig> cdcConfigs) {
        return cdcConfigs.stream()
                .map(c -> c.getDb() + "." + c.getTbl())
                .distinct()
                .collect(Collectors.joining(","));
    }

    /**
     * 从配置列表构建数据库列表字符串（仅包含启用状态的表）
     *
     * @param cdcConfigs cdc配置列表
     * @return 数据库列表（逗号分隔，去重）
     */
    default String buildDatabaseIncludeListEnabled(List<CdcConfig> cdcConfigs) {
        return cdcConfigs.stream()
                .filter(c -> Boolean.TRUE.equals(c.getEnabled()))
                .map(CdcConfig::getDb)
                .distinct()
                .collect(Collectors.joining(","));
    }

    /**
     * 从配置列表构建表列表字符串（仅包含启用状态的表）
     *
     * @param cdcConfigs cdc配置列表
     * @return 表列表（逗号分隔，格式：db.table）
     */
    default String buildTableIncludeListEnabled(List<CdcConfig> cdcConfigs) {
        return cdcConfigs.stream()
                .filter(c -> Boolean.TRUE.equals(c.getEnabled()))
                .map(c -> c.getDb() + "." + c.getTbl())
                .distinct()
                .collect(Collectors.joining(","));
    }
}
