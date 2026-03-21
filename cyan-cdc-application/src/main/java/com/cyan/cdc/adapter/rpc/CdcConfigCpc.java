package com.cyan.cdc.adapter.rpc;

import com.cyan.arch.common.api.Response;
import com.cyan.cdc.adapter.convert.CdcConfigAdapterConvert;
import com.cyan.cdc.adapter.http.dto.CdcConfigDTO;
import com.cyan.cdc.app.bo.CdcConfigBO;
import com.cyan.cdc.app.service.CdcConfigQueryService;
import com.cyan.cdc.domain.query.CdcConfigListQuery;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

/**
 * 数据源控制器
 *
 * @author cy.Y
 * @since 1.0.0
 */
@RestController
@RequestMapping("/rpc/v1/cdc")
public class CdcConfigCpc {

    private final CdcConfigQueryService cDCConfigQueryService;

    public CdcConfigCpc(CdcConfigQueryService cDCConfigQueryService) {
        this.cDCConfigQueryService = cDCConfigQueryService;
    }


    /**
     * 查询cdc配置列表
     */
    @GetMapping("/configs")
    public Response<List<CdcConfigDTO>> list() {
        List<CdcConfigBO> cdcConfigBOS = cDCConfigQueryService.list(new CdcConfigListQuery());
        List<CdcConfigDTO> list = Optional.ofNullable(cdcConfigBOS).orElse(List.of()).stream().map(CdcConfigAdapterConvert.INSTANCE::toDatasourceInfoDTO).toList();
        return Response.success(list);
    }

}
