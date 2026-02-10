package com.cyan.cdc.infra.convert;

import com.cyan.arch.common.mapstruct.MapstructConvert;
import com.cyan.cdc.domain.CdcConfig;
import com.cyan.cdc.infra.dos.CdcConfigDO;
import com.cyan.cdc.infra.rpc.request.ConnectorSaveRequest;
import com.cyan.cdc.infra.rpc.request.config.MySQLConnectorConfig;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

/**
 * @author cy.Y
 * @since 1.0.0
 */
@Mapper(uses = MapstructConvert.class)
public interface CdcConfigInfraConvert {

    CdcConfigInfraConvert INSTANCE = Mappers.getMapper(CdcConfigInfraConvert.class);

    CdcConfigDO toCdcConfigDO(CdcConfig CDCConfig);

    CdcConfig toCdcConfig(CdcConfigDO CDCConfigDO);

    default ConnectorSaveRequest toConnectorSaveRequest(CdcConfig cdcConfig, String kafkaUrl) {
        MySQLConnectorConfig config = new MySQLConnectorConfig()
                .setTaskMax("1")
                .setHostname(cdcConfig.getHostname())
                .setPort(cdcConfig.getPort())
                .setUser(cdcConfig.getUsername())
                .setPassword(cdcConfig.getPassword())
                .setServerId(cdcConfig.getServerId())
                .setTopicPrefix(cdcConfig.getConnectorName())
                .setDatabaseIncludeList(cdcConfig.getDb())
                .setTableIncludeList(cdcConfig.getDb() + "." + cdcConfig.getTbl())
                .setKafkaTopic(cdcConfig.getConnectorName())
                .setKafkaBootstrapServers(kafkaUrl)
                .setIncludeSchemaChanges(true);
        return new ConnectorSaveRequest()
                .setName(cdcConfig.getConnectorName())
                .setConfig(config);
    }
}
