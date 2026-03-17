package com.cyan.cdc.infra.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cyan.arch.common.api.SilentException;
import com.cyan.arch.common.util.CollUtils;
import com.cyan.arch.common.util.JSON;
import com.cyan.cdc.client.enums.RunningStatus;
import com.cyan.cdc.domain.CdcConfig;
import com.cyan.cdc.domain.query.CdcConfigListQuery;
import com.cyan.cdc.infra.convert.CdcConfigInfraConvert;
import com.cyan.cdc.infra.dos.CdcConfigDO;
import com.cyan.cdc.infra.mapper.CdcConfigMapper;
import com.cyan.cdc.infra.repository.CdcConfigRepository;
import com.cyan.cdc.infra.rpc.DebeziumRPC;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * cdc服务
 *
 * @author cy.Y
 * @since 1.0.0
 */
@Repository
@Slf4j
public class CdcConfigRepositoryImpl implements CdcConfigRepository {
    @Value("${kafka.url}")
    private String kafkaUrl;

    private final CdcConfigMapper cdcConfigMapper;
    private final DebeziumRPC debeziumRPC;

    public CdcConfigRepositoryImpl(CdcConfigMapper cdcConfigMapper, DebeziumRPC debeziumRPC) {
        this.cdcConfigMapper = cdcConfigMapper;
        this.debeziumRPC = debeziumRPC;
    }

    /**
     * 保存cdc信息
     * 一个数据源实例（hostname+port+username）对应一个连接器
     *
     * @return cdc信息
     */
    @Override
    public CdcConfig save(CdcConfig cdcConfig) {
        // 默认启用
        cdcConfig.setEnabled(true);
        
        // 查询该数据源是否已存在配置
        List<CdcConfig> existingConfigs = listByDatasource(
                cdcConfig.getHostname(), 
                cdcConfig.getPort(), 
                cdcConfig.getUsername()
        );
        
        CdcConfigDO cdcConfigDO = CdcConfigInfraConvert.INSTANCE.toCdcConfigDO(cdcConfig);
        cdcConfigDO.setRunningStatus(RunningStatus.RUNNING);
        cdcConfigDO.setEnabled(true);
        
        if (existingConfigs.isEmpty()) {
            // 数据源不存在，创建新连接器
            int serverId = cdcConfigMapper.findAServerId();
            // 连接器名称使用：数据源名称_主机_端口
            String connectorName = "%s_%s_%s".formatted(
                    cdcConfig.getName(), 
                    cdcConfig.getHostname().replace(".", "_"), 
                    cdcConfig.getPort()
            );
            cdcConfig.setServerId(serverId)
                    .setConnectorName(connectorName);
            cdcConfigDO.setServerId(serverId)
                    .setConnectorName(connectorName);
            
            try {
                cdcConfigMapper.insert(cdcConfigDO);
            } catch (DuplicateKeyException e) {
                throw new SilentException("cdc配置已存在,请检查name是否重复或（主机+端口+库+表）组合是否已存在");
            }
            
            // 创建连接器
            cdcConfig.setId(cdcConfigDO.getId() + "");
            String databaseIncludeList = cdcConfig.getDb();
            String tableIncludeList = cdcConfig.getDb() + "." + cdcConfig.getTbl();
            Object connector = debeziumRPC.createConnector(
                    CdcConfigInfraConvert.INSTANCE.toConnectorSaveRequest(cdcConfig, kafkaUrl, databaseIncludeList, tableIncludeList)
            );
            log.info("debezium连接器创建成功: {}", JSON.toJSONString(connector));
        } else {
            // 数据源已存在，复用连接器，更新table.include.list
            CdcConfig existingConfig = existingConfigs.get(0);
            // 继承已存在配置的name和连接器信息，避免唯一约束冲突
            cdcConfig.setServerId(existingConfig.getServerId())
                    .setConnectorName(existingConfig.getConnectorName())
                    .setName(existingConfig.getName());
            cdcConfigDO.setServerId(existingConfig.getServerId())
                    .setConnectorName(existingConfig.getConnectorName())
                    .setName(existingConfig.getName());
            
            try {
                cdcConfigMapper.insert(cdcConfigDO);
            } catch (DuplicateKeyException e) {
                throw new SilentException("cdc配置的表已存在,请检查（主机+端口+库+表）组合是否已存在");
            }
            
            cdcConfig.setId(cdcConfigDO.getId() + "");
            
            // 获取该数据源下所有配置（包括新插入的），只包含启用的表
            List<CdcConfig> allConfigs = listByDatasource(
                    cdcConfig.getHostname(), 
                    cdcConfig.getPort(), 
                    cdcConfig.getUsername()
            );
            String databaseIncludeList = CdcConfigInfraConvert.INSTANCE.buildDatabaseIncludeListEnabled(allConfigs);
            String tableIncludeList = CdcConfigInfraConvert.INSTANCE.buildTableIncludeListEnabled(allConfigs);
            
            // 更新连接器配置
            Object connector = debeziumRPC.updateConnector(
                    cdcConfig.getConnectorName(),
                    CdcConfigInfraConvert.INSTANCE.toMySQLConnectorConfig(cdcConfig, kafkaUrl, databaseIncludeList, tableIncludeList)
            );
            log.info("debezium连接器更新成功: {}", JSON.toJSONString(connector));
        }
        
        cdcConfigDO = cdcConfigMapper.selectById(cdcConfigDO.getId());
        return CdcConfigInfraConvert.INSTANCE.toCdcConfig(cdcConfigDO);
    }

    /**
     * 根据id查询cdc信息
     *
     * @param id 主键id
     * @return cdc信息
     */
    @Override
    public CdcConfig queryById(String id) {
        CdcConfigDO cdcConfigDO = cdcConfigMapper.selectById(id);
        return CdcConfigInfraConvert.INSTANCE.toCdcConfig(cdcConfigDO);
    }

    /**
     * 查询所cdc信息
     *
     * @return cdc信息
     */
    @Override
    public List<CdcConfig> list(CdcConfigListQuery query) {
        query = query == null ? new CdcConfigListQuery() : query;
        LambdaQueryWrapper<CdcConfigDO> wrapper = new LambdaQueryWrapper<CdcConfigDO>()
                .in(CollUtils.isNotEmpty(query.getRunningStatuses()), CdcConfigDO::getRunningStatus, query.getRunningStatuses());
        List<CdcConfigDO> cdcConfigDOS = cdcConfigMapper.selectList(wrapper);
        return Optional.ofNullable(cdcConfigDOS).orElse(List.of()).stream().map(CdcConfigInfraConvert.INSTANCE::toCdcConfig).toList();
    }

    /**
     * 更新cdc信息
     *
     * @param cdcConfig cdc信息
     */
    @Override
    public void update(CdcConfig cdcConfig) {
        CdcConfigDO cdcConfigDO = CdcConfigInfraConvert.INSTANCE.toCdcConfigDO(cdcConfig);
        cdcConfigMapper.updateById(cdcConfigDO);
    }

    /**
     * 删除cdc信息
     *
     * @param id 主键
     */
    @Override
    public void delete(String id) {
        LambdaQueryWrapper<CdcConfigDO> queryWrapper = new LambdaQueryWrapper<CdcConfigDO>()
                .eq(CdcConfigDO::getId, id);
        cdcConfigMapper.delete(queryWrapper);
    }

    /**
     * 按数据源实例查询所有cdc配置
     *
     * @param hostname 主机地址
     * @param port     端口
     * @param username 用户名
     * @return 该数据源下的所有cdc配置
     */
    @Override
    public List<CdcConfig> listByDatasource(String hostname, String port, String username) {
        LambdaQueryWrapper<CdcConfigDO> queryWrapper = new LambdaQueryWrapper<CdcConfigDO>()
                .eq(CdcConfigDO::getHostname, hostname)
                .eq(CdcConfigDO::getPort, port)
                .eq(CdcConfigDO::getUsername, username);
        List<CdcConfigDO> cdcConfigDOS = cdcConfigMapper.selectList(queryWrapper);
        return Optional.ofNullable(cdcConfigDOS).orElse(List.of()).stream().map(CdcConfigInfraConvert.INSTANCE::toCdcConfig).toList();
    }

    /**
     * 按连接器名称查询所有cdc配置
     *
     * @param connectorName 连接器名称
     * @return 使用该连接器的所有cdc配置
     */
    @Override
    public List<CdcConfig> listByConnectorName(String connectorName) {
        LambdaQueryWrapper<CdcConfigDO> queryWrapper = new LambdaQueryWrapper<CdcConfigDO>()
                .eq(CdcConfigDO::getConnectorName, connectorName);
        List<CdcConfigDO> cdcConfigDOS = cdcConfigMapper.selectList(queryWrapper);
        return Optional.ofNullable(cdcConfigDOS).orElse(List.of()).stream().map(CdcConfigInfraConvert.INSTANCE::toCdcConfig).toList();
    }

    /**
     * 检查数据源实例是否存在
     *
     * @param hostname 主机地址
     * @param port     端口
     * @param username 用户名
     * @return 是否存在
     */
    @Override
    public boolean existsByDatasource(String hostname, String port, String username) {
        LambdaQueryWrapper<CdcConfigDO> queryWrapper = new LambdaQueryWrapper<CdcConfigDO>()
                .eq(CdcConfigDO::getHostname, hostname)
                .eq(CdcConfigDO::getPort, port)
                .eq(CdcConfigDO::getUsername, username);
        return cdcConfigMapper.selectCount(queryWrapper) > 0;
    }
}
