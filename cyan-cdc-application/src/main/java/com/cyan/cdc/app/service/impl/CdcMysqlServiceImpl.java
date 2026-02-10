package com.cyan.cdc.app.service.impl;

import com.cyan.arch.common.api.SilentException;
import com.cyan.cdc.app.bo.CdcConfigBO;
import com.cyan.cdc.app.service.CdcConfigCmdService;
import com.cyan.cdc.app.service.CdcConfigQueryService;
import com.cyan.cdc.app.service.CdcService;
import com.cyan.cdc.client.enums.DatasourceType;
import com.cyan.cdc.client.enums.RunningStatus;
import com.cyan.cdc.infra.rpc.DebeziumRPC;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * cdc 服务
 *
 * @author cy.Y
 * @since 1.0.0
 */
@Service
@Transactional(rollbackFor = Exception.class)
public class CdcMysqlServiceImpl implements CdcService {

    private final CdcConfigQueryService cdcConfigQueryService;
    private final CdcConfigCmdService cDCConfigCmdService;
    private final DebeziumRPC debeziumRPC;

    public CdcMysqlServiceImpl(CdcConfigQueryService cdcConfigQueryService, @Lazy CdcConfigCmdService cDCConfigCmdService, DebeziumRPC debeziumRPC) {
        this.cdcConfigQueryService = cdcConfigQueryService;
        this.cDCConfigCmdService = cDCConfigCmdService;
        this.debeziumRPC = debeziumRPC;
    }

    /**
     * 启动cdc服务
     *
     * @param id cdc-config id
     */
    @Override
    @Async
    public void start(String id) {
        CdcConfigBO cdcConfigBO = cdcConfigQueryService.queryById(id);
        if (cdcConfigBO == null) {
            throw new SilentException(id + ":cdc-配置信息不存在");
        }
        try {
            debeziumRPC.startConnector(cdcConfigBO.getConnectorName());
            //修改为运行状态
            cDCConfigCmdService.updateStatus(id, RunningStatus.RUNNING, null);
        } catch (Exception e) {
            cDCConfigCmdService.updateStatus(id, RunningStatus.ERROR, e.getMessage());
        }
    }

    /**
     * 获取数据源类型
     *
     * @return 数据源类型
     */
    @Override
    public DatasourceType getDatasourceType() {
        return DatasourceType.MYSQL;
    }

    /**
     * 停止cdc服务
     *
     * @param id cdc-config id
     */
    @Override
    public void stop(String id) {
        CdcConfigBO cdcConfigBO = cdcConfigQueryService.queryById(id);
        if (cdcConfigBO == null) {
            throw new SilentException(id + ":cdc-配置信息不存在");
        }
        try {
            debeziumRPC.startConnector(cdcConfigBO.getConnectorName());
            //修改为运行状态
            cDCConfigCmdService.updateStatus(id, RunningStatus.STOP, null);
        } catch (Exception e) {
            cDCConfigCmdService.updateStatus(id, RunningStatus.ERROR, e.getMessage());
        }
    }
}
