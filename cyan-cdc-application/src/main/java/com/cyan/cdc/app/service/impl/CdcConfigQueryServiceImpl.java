package com.cyan.cdc.app.service.impl;

import com.cyan.cdc.app.bo.CdcConfigBO;
import com.cyan.cdc.app.cmd.CDCConfigCmd;
import com.cyan.cdc.app.convert.CdcConfigAppConvert;
import com.cyan.cdc.app.service.CdcConfigQueryService;
import com.cyan.cdc.domain.CdcConfig;
import com.cyan.cdc.domain.query.CdcConfigListQuery;
import com.cyan.cdc.infra.repository.CdcConfigRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * cdc-config查询服务
 *
 * @author cy.Y
 * @since 1.0.0
 */
@Service
public class CdcConfigQueryServiceImpl implements CdcConfigQueryService {
    private final CdcConfigRepository cdcConfigRepository;

    public CdcConfigQueryServiceImpl(CdcConfigRepository cdcConfigRepository) {
        this.cdcConfigRepository = cdcConfigRepository;
    }

    /**
     * 根据id查询 cdc-config
     *
     * @param id cdc-config id
     */
    @Override
    public CdcConfigBO queryById(String id) {
        CdcConfig cdcConfig = cdcConfigRepository.queryById(id);
        return CdcConfigAppConvert.INSTANCE.toDatasourceInfoBO(cdcConfig);
    }

    /**
     * 查询所有 cdc-config
     */
    @Override
    public List<CdcConfigBO> list(CdcConfigListQuery query) {
        List<CdcConfig> cdcConfigs = cdcConfigRepository.list(query);
        return Optional.ofNullable(cdcConfigs).orElse(List.of()).stream().map(CdcConfigAppConvert.INSTANCE::toDatasourceInfoBO).toList();
    }

    /**
     * 回显cdc配置信息
     *
     * @param id cdc-config id
     */
    @Override
    public CDCConfigCmd echo(String id) {
        CdcConfigBO cdcConfigBO = queryById(id);
        return CdcConfigAppConvert.INSTANCE.toCDCConfigCmd(cdcConfigBO);
    }
}
