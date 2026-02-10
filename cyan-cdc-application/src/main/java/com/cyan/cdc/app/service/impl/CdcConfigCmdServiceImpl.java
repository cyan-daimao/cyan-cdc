package com.cyan.cdc.app.service.impl;

import com.cyan.arch.common.api.BusinessException;
import com.cyan.arch.common.api.SilentException;
import com.cyan.arch.common.util.StrUtils;
import com.cyan.cdc.app.bo.CdcConfigBO;
import com.cyan.cdc.app.cmd.CDCConfigCmd;
import com.cyan.cdc.app.cmd.CDCStartCmd;
import com.cyan.cdc.app.cmd.CdcDeleteCmd;
import com.cyan.cdc.app.convert.CdcConfigAppConvert;
import com.cyan.cdc.app.service.CdcConfigCmdService;
import com.cyan.cdc.app.service.CdcConfigQueryService;
import com.cyan.cdc.app.service.CdcService;
import com.cyan.cdc.client.enums.DatasourceType;
import com.cyan.cdc.client.enums.RunningStatus;
import com.cyan.cdc.domain.CdcConfig;
import com.cyan.cdc.infra.repository.CdcConfigRepository;
import com.cyan.cdc.infra.rpc.DebeziumRPC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 数据源信息服务
 *
 * @author cy.Y
 * @since 1.0.0
 */
@Service
@Transactional(rollbackFor = Exception.class)
public class CdcConfigCmdServiceImpl implements CdcConfigCmdService {

    private final CdcConfigRepository cdcConfigRepository;
    private final Map<DatasourceType, CdcService> cdcServiceMap = new EnumMap<>(DatasourceType.class);
    private final DebeziumRPC debeziumRPC;
    private final CdcConfigQueryService cdcConfigQueryService;

    public CdcConfigCmdServiceImpl(CdcConfigRepository cdcConfigRepository, List<CdcService> cdcServices, DebeziumRPC debeziumRPC, CdcConfigQueryService cdcConfigQueryService) {
        this.cdcConfigRepository = cdcConfigRepository;
        Optional.ofNullable(cdcServices).orElse(List.of()).forEach(cdcService -> cdcServiceMap.put(cdcService.getDatasourceType(), cdcService));
        this.debeziumRPC = debeziumRPC;
        this.cdcConfigQueryService = cdcConfigQueryService;
    }

    /**
     * 保存数据源信息
     */
    @Override
    public CdcConfigBO save(CDCConfigCmd cmd) {
        CdcConfig cdcConfig = CdcConfigAppConvert.INSTANCE.toDatasourceInfo(cmd);
        cdcConfig = cdcConfig.save(cdcConfigRepository);
        return CdcConfigAppConvert.INSTANCE.toDatasourceInfoBO(cdcConfig);
    }

    /**
     * 更新cdc配置信息
     */
    @Override
    public void update(CDCConfigCmd cmd) {
        if (StrUtils.isBlank(cmd.getId())) {
            throw new SilentException("cdc-config id不能为空");
        }
    }

    /**
     * 启动cdc任务
     *
     * @param cmd 启动参数
     */
    @Override
    public void start(CDCStartCmd cmd) {
        CdcConfig cdcConfig = cdcConfigRepository.queryById(cmd.getId());
        //启动cdc任务
        cdcServiceMap.get(cdcConfig.getDatasourceType()).start(cmd.getId());
    }

    /**
     * 停止cdc任务
     *
     * @param cmd 停止参数
     */
    @Override
    public void stop(CDCStartCmd cmd) {
        CdcConfig cdcConfig = cdcConfigRepository.queryById(cmd.getId());
        if (cdcConfig == null) {
            throw new SilentException("cdc-config不存在");
        }
        //停止cdc任务
        try {
            cdcServiceMap.get(cdcConfig.getDatasourceType()).stop(cmd.getId());
            updateStatus(cmd.getId(), RunningStatus.STOP, null);
        } catch (Exception e) {
            updateStatus(cmd.getId(), RunningStatus.RUNNING, e.getMessage());
            throw new BusinessException(e.getMessage());
        }
    }

    /**
     * 更新cdc任务状态
     *
     * @param id     cdc任务id
     * @param status 运行状态
     * @param msg    运行信息
     */
    @Override
    public void updateStatus(String id, RunningStatus status, String msg) {
        CdcConfig cdcConfig = cdcConfigRepository.queryById(id);
        cdcConfig.setRunningStatus(status)
                .setMsg(msg);
        cdcConfig.update(cdcConfigRepository);
    }

    /**
     * 删除cdc任务
     *
     */
    @Override
    public void delete(CdcDeleteCmd cmd) {
        CdcConfigBO cdcConfigBO = cdcConfigQueryService.queryById(cmd.getId());
        debeziumRPC.deleteConnector(cdcConfigBO.getConnectorName());
        cdcConfigRepository.delete(cmd.getId());
    }
}
