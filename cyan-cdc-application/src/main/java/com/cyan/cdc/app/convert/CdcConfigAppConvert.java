package com.cyan.cdc.app.convert;

import com.cyan.arch.common.mapstruct.MapstructConvert;
import com.cyan.cdc.app.bo.CdcConfigBO;
import com.cyan.cdc.app.cmd.CDCConfigCmd;
import com.cyan.cdc.domain.CdcConfig;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

/**
 * @author cy.Y
 * @since 1.0.0
 */
@Mapper(uses = MapstructConvert.class)
public interface CdcConfigAppConvert {

    CdcConfigAppConvert INSTANCE = Mappers.getMapper(CdcConfigAppConvert.class);

    CDCConfigCmd toCDCConfigCmd(CdcConfigBO cdcConfigBO);

    CdcConfig toDatasourceInfo(CDCConfigCmd cmd);

    CdcConfigBO toDatasourceInfoBO(CdcConfig CDCConfig);
}
