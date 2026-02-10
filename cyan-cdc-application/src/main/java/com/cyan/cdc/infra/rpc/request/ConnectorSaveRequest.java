package com.cyan.cdc.infra.rpc.request;

import com.cyan.cdc.infra.rpc.request.config.ConnectorConfig;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * mysql的数据库连接器请求
 *
 * @author cy.Y
 * @since 1.0.0
 */
@AllArgsConstructor
@NoArgsConstructor
@Data
@Accessors(chain = true)
public class ConnectorSaveRequest {
    /**
     * 连接器名称:"mysql-cdc-test"
     */
    private String name;

    /**
     * 配置信息
     */
    private ConnectorConfig config;
}
