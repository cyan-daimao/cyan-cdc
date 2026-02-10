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
     *
     * @return cdc信息
     */
    @Override
    public CdcConfig save(CdcConfig cdcConfig) {
        int serverId = cdcConfigMapper.findAServerId();
        String name = "%s_%s_%s".formatted(cdcConfig.getName(), cdcConfig.getDb(), cdcConfig.getTbl());
        cdcConfig.setServerId(serverId)
                .setConnectorName(name);
        CdcConfigDO cdcConfigDO = CdcConfigInfraConvert.INSTANCE.toCdcConfigDO(cdcConfig);
        cdcConfigDO.setRunningStatus(RunningStatus.RUNNING);
        try {
            cdcConfigMapper.insert(cdcConfigDO);
        } catch (DuplicateKeyException e) {
            throw new SilentException("cdc配置的表已存在,请检查name或（主机+端口+库+表）名是否已存在");
        }
        //调用debezium创建
        cdcConfig.setId(cdcConfigDO.getId() + "");
        Object connector = debeziumRPC.createConnector(CdcConfigInfraConvert.INSTANCE.toConnectorSaveRequest(cdcConfig, kafkaUrl));
        log.info("debezium创建成功: {}", JSON.toJSONString(connector));
        cdcConfigDO = cdcConfigMapper.selectById(cdcConfigDO.getId());
        return CdcConfigInfraConvert.INSTANCE.toCdcConfig(cdcConfigDO);
    }

    /**
     * 根据id查询cdc信息
     *
     * @param id cdcid
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
}
