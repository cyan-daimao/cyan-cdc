package com.cyan.cdc.app.service;

import com.cyan.cdc.client.enums.DatasourceType;

/**
 * cdc服务
 *
 * @author cy.Y
 * @since 1.0.0
 */
public interface CdcService {

    /**
     * 启动cdc服务
     *
     * @param id cdc-config id
     */
    void start(String id);


    /**
     * 获取数据源类型
     *
     * @return 数据源类型
     */
    DatasourceType getDatasourceType();

    /**
     * 停止cdc服务
     *
     * @param id cdc-config id
     */
    void stop(String id);
}
