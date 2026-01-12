package com.cyan.cdc.app.service;

import com.cyan.cdc.app.bo.CdcConfigBO;
import com.cyan.cdc.app.cmd.CDCConfigCmd;

import java.util.List;

/**
 * cdc-config 查询服务
 * @author cy.Y
 * @since 1.0.0
 */
public interface CdcConfigQueryService {

    /**
     * 根据id查询 cdc-config
     */
    CdcConfigBO queryById(String id);

    /**
     * 查询所有 cdc-config
     */
    List<CdcConfigBO> list();

    /**
     * 回显cdc配置信息
     */
    CDCConfigCmd echo(String id);
}
