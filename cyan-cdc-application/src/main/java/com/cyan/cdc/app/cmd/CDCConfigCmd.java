package com.cyan.cdc.app.cmd;

import com.cyan.cdc.client.enums.DatasourceType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * 数据源编辑命令
 *
 * @author cy.Y
 * @since 1.0.0
 */
@AllArgsConstructor
@NoArgsConstructor
@Data
@Accessors(chain = true)
public class CDCConfigCmd {
    /**
     * 主键id
     */
    private String id;

    /**
     * 数据源名称
     */
    @NotBlank(message = "数据源名称不能为空")
    private String name;

    /**
     * 数据源类型
     */
    @NotNull(message = "数据源类型不能为空")
    private DatasourceType datasourceType;

    /**
     * 数据源连接地址
     */
    @NotBlank(message = "数据源连接地址不能为空")
    private String hostname;

    /**
     * 数据源连接地址
     */
    @NotBlank(message = "端口号不能为空")
    private String port;

    /**
     * 数据库名称
     */
    @NotBlank(message = "数据库名称不能为空")
    private String db;

    /**
     * 表名称
     */
    @NotBlank(message = "表名称不能为空")
    private String tbl;

    /**
     * 数据源用户名
     */
    @NotBlank(message = "数据源用户名不能为空")
    private String username;

    /**
     * 数据源密码
     */
    @NotBlank(message = "数据源密码不能为空")
    private String password;

}
