package com.cyan.cdc.adapter.http.dto;

import com.cyan.cdc.client.enums.DatasourceType;
import com.cyan.cdc.client.enums.RunningStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 数据源信息
 *
 * @author cy.Y
 * @since 1.0.0
 */
@AllArgsConstructor
@NoArgsConstructor
@Data
@Accessors(chain = true)
public class CdcConfigDTO {
    /**
     * 主键id
     */
    private String id;

    /**
     * 数据源名称
     */
    private String name;

    /**
     * 数据源类型
     */
    private DatasourceType datasourceType;

    /**
     * 数据源连接地址
     */
    private String hostname;

    /**
     * 数据源端口
     */
    private String port;

    /**
     * 数据库
     */
    private String db;

    /**
     * 表
     */
    private String tbl;
    
    /**
     * 数据源用户名
     */
    private String username;

    /**
     * 数据源密码
     */
    private String password;

    /**
     * 运行状态
     */
    private RunningStatus runningStatus;

    /**
     * 运行信息
     */
    private String msg;
    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;

}
