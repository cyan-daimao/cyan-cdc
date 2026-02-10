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
}
