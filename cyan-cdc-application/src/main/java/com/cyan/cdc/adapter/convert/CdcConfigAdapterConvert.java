package com.cyan.cdc.adapter.convert;

import com.cyan.arch.common.mapstruct.MapstructConvert;
import com.cyan.cdc.adapter.http.dto.CdcConfigDTO;
import com.cyan.cdc.app.bo.CdcConfigBO;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

/**
 * @author cy.Y
 * @since 1.0.0
 */
@Mapper(uses = MapstructConvert.class)
public interface CdcConfigAdapterConvert {

    CdcConfigAdapterConvert INSTANCE = Mappers.getMapper(CdcConfigAdapterConvert.class);

    CdcConfigDTO toDatasourceInfoDTO(CdcConfigBO CDCConfigBO);
}
