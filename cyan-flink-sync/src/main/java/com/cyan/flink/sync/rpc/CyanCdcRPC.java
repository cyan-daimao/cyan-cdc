package com.cyan.flink.sync.rpc;

import com.cy.easyhttp.HttpClient;
import com.cy.easyhttp.annotation.method.Get;

import java.util.List;

/**
 * cdc的rpc服务
 *
 * @author cy.Y
 * @since 1.0.0
 */
@HttpClient(baseUrl = "http://cyan-cdc.cyan.com")
public interface CyanCdcRPC {

    /**
     * 查询cdc配置列表
     */
    @Get("/rpc/v1/cdc/configs")
    Response<List<CdcConfigDTO>> list();
}
