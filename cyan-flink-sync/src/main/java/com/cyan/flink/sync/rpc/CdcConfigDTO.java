package com.cyan.flink.sync.rpc;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;


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
     * 数据源类型: MYSQL
     */
    private String datasourceType;

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
     * 运行状态: running
     */
    private String runningStatus;

    /**
     * 运行信息
     */
    private String msg;
    /**
     * 创建时间
     */
    private String createdAt;

    /**
     * 更新时间
     */
    private String updatedAt;

}
