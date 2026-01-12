package com.cyan.cdc.infra.repository.impl;

import com.cyan.arch.common.api.SilentException;
import com.cyan.cdc.domain.CdcConfig;
import com.cyan.cdc.infra.convert.CdcConfigInfraConvert;
import com.cyan.cdc.infra.dos.CdcConfigDO;
import com.cyan.cdc.infra.mapper.CdcConfigMapper;
import com.cyan.cdc.infra.repository.CdcConfigRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * cdc服务
 *
 * @author cy.Y
 * @since 1.0.0
 */
@Repository
public class CdcConfigRepositoryImpl implements CdcConfigRepository {
    private final CdcConfigMapper cdcConfigMapper;

    public CdcConfigRepositoryImpl(CdcConfigMapper cdcConfigMapper) {
        this.cdcConfigMapper = cdcConfigMapper;
    }

    /**
     * 保存cdc信息
     *
     * @return cdc信息
     */
    @Override
    public CdcConfig save(CdcConfig CDCConfig) {
        CdcConfigDO CDCConfigDO = CdcConfigInfraConvert.INSTANCE.toCdcConfigDO(CDCConfig);
        try {
            cdcConfigMapper.insert(CDCConfigDO);
        } catch (DuplicateKeyException e) {
            throw new SilentException("cdc配置的表已存在,请检查name或（主机+端口+库+表）名是否已存在");
        }
        CDCConfigDO = cdcConfigMapper.selectById(CDCConfigDO.getId());
        return CdcConfigInfraConvert.INSTANCE.toCdcConfig(CDCConfigDO);
    }

    /**
     * 根据id查询cdc信息
     *
     * @param id cdcid
     * @return cdc信息
     */
    @Override
    public CdcConfig queryById(String id) {
        CdcConfigDO cdcConfigDO = cdcConfigMapper.selectById(id);
        return CdcConfigInfraConvert.INSTANCE.toCdcConfig(cdcConfigDO);
    }

    /**
     * 查询所cdc信息
     *
     * @return cdc信息
     */
    @Override
    public List<CdcConfig> list() {
        List<CdcConfigDO> cdcConfigDOS = cdcConfigMapper.selectList(null);
        return Optional.ofNullable(cdcConfigDOS).orElse(List.of()).stream().map(CdcConfigInfraConvert.INSTANCE::toCdcConfig).toList();
    }

    /**
     * 更新cdc信息
     *
     * @param cdcConfig cdc信息
     */
    @Override
    public void update(CdcConfig cdcConfig) {
        CdcConfigDO cdcConfigDO = CdcConfigInfraConvert.INSTANCE.toCdcConfigDO(cdcConfig);
        cdcConfigMapper.updateById(cdcConfigDO);
    }
}
