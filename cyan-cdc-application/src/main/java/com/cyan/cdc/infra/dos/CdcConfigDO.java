package com.cyan.cdc.infra.dos;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
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
@TableName("cdc_config")
public class CdcConfigDO {

    /**
     * 主键id
     */
    @TableId(value = "id")
    private Long id;

    /**
     * 数据源名称
     */
    @TableField(value = "name")
    private String name;

    /**
     * 数据源类型
     */
    @TableField(value = "datasource_type")
    private DatasourceType datasourceType;

    /**
     * 数据源连接地址
     */
    @TableField(value = "hostname")
    private String hostname;

    /**
     * 数据源端口
     */
    @TableField(value = "port")
    private String port;

    /**
     * 数据源数据库名称
     */
    @TableField(value = "db")
    private String db;

    /**
     * 数据源表名称
     */
    @TableField(value = "tbl")
    private String tbl;

    /**
     * 数据源用户名
     */
    @TableField(value = "username")
    private String username;

    /**
     * 数据源密码
     */
    @TableField(value = "password")
    private String password;

    /**
     * 运行状态
     */
    @TableField(value = "running_status")
    private RunningStatus runningStatus;

    /**
     * 运行信息
     */
    @TableField(value = "msg")
    private String msg;

    /**
     * debezium的server-id
     */
    @TableField(value = "server_id")
    private Integer serverId;

    /**
     * debezium的连接器名称(唯一)
     */
    @TableField(value = "connector_name")
    private String connectorName;

    /**
     * 创建时间
     */
    @TableField(value = "created_at")
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    @TableField(value = "updated_at")
    private LocalDateTime updatedAt;

    /**
     * 删除时间
     */
    @TableField(value = "deleted_at")
    private LocalDateTime deletedAt;
}
