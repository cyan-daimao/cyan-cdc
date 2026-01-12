package com.cyan.cdc.infra.convert;

import com.cyan.arch.common.mapstruct.MapstructConvert;
import com.cyan.cdc.domain.CdcConfig;
import com.cyan.cdc.infra.dos.CdcConfigDO;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

/**
 * @author cy.Y
 * @since 1.0.0
 */
@Mapper(uses = MapstructConvert.class)
public interface CdcConfigInfraConvert {

    CdcConfigInfraConvert INSTANCE = Mappers.getMapper(CdcConfigInfraConvert.class);

    CdcConfigDO toCdcConfigDO(CdcConfig CDCConfig);

    CdcConfig toCdcConfig(CdcConfigDO CDCConfigDO);
}
