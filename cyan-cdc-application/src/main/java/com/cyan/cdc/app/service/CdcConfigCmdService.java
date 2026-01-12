package com.cyan.cdc.app.service;

import com.cyan.cdc.app.bo.CdcConfigBO;
import com.cyan.cdc.app.cmd.CDCConfigCmd;
import com.cyan.cdc.app.cmd.CDCStartCmd;
import com.cyan.cdc.client.enums.RunningStatus;

/**
 * 数据源信息服务
 *
 * @author cy.Y
 * @since 1.0.0
 */
public interface CdcConfigCmdService {

    /**
     * 保存cdc配置信息
     */
    CdcConfigBO save(CDCConfigCmd cmd);

    /**
     * 更新cdc配置信息
     */
    void update(CDCConfigCmd cmd);

    /**
     * 启动cdc任务
     */
    void start(CDCStartCmd cmd);

    /**
     * 停止cdc任务
     */
    void stop(CDCStartCmd cmd);

    /**
     * 更新cdc任务状态
     * @param id cdc任务id
     * @param status 运行状态
     * @param msg 运行信息
     */
    void updateStatus(String id, RunningStatus status,String msg);
}
