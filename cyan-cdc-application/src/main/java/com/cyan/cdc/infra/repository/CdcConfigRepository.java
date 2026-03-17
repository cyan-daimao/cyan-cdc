package com.cyan.cdc.infra.repository;

import com.cyan.cdc.domain.CdcConfig;
import com.cyan.cdc.domain.query.CdcConfigListQuery;

import java.util.List;

/**
 * cdc仓储服务
 *
 * @author cy.Y
 * @since 1.0.0
 */
public interface CdcConfigRepository {

    /**
     * 保存cdc信息
     *
     * @return cdc信息
     */
    CdcConfig save(CdcConfig CDCConfig);

    /**
     * 根据id查询cdc信息
     *
     * @param id cdcid
     * @return cdc信息
     */
    CdcConfig queryById(String id);

    /**
     * 查询所有cdc信息
     *
     * @return cdc信息
     */
    List<CdcConfig> list(CdcConfigListQuery query);

    /**
     * 更新cdc信息
     *
     * @param cdcConfig cdc信息
     */
    void update(CdcConfig cdcConfig);

    /**
     * 删除cdc信息
     * @param id 主键
     */
    void delete(String id);

    /**
     * 按数据源实例查询所有cdc配置
     * 数据源实例由 hostname + port + username 唯一标识
     *
     * @param hostname 主机地址
     * @param port     端口
     * @param username 用户名
     * @return 该数据源下的所有cdc配置
     */
    List<CdcConfig> listByDatasource(String hostname, String port, String username);

    /**
     * 按连接器名称查询所有cdc配置
     *
     * @param connectorName 连接器名称
     * @return 使用该连接器的所有cdc配置
     */
    List<CdcConfig> listByConnectorName(String connectorName);

    /**
     * 检查数据源实例是否存在
     *
     * @param hostname 主机地址
     * @param port     端口
     * @param username 用户名
     * @return 是否存在
     */
    boolean existsByDatasource(String hostname, String port, String username);
}
