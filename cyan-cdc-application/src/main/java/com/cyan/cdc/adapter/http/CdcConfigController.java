package com.cyan.cdc.adapter.http;

import com.cyan.arch.common.api.Response;
import com.cyan.cdc.adapter.convert.CdcConfigAdapterConvert;
import com.cyan.cdc.adapter.http.dto.CdcConfigDTO;
import com.cyan.cdc.app.bo.CdcConfigBO;
import com.cyan.cdc.app.cmd.CDCStartCmd;
import com.cyan.cdc.app.service.CdcConfigCmdService;
import com.cyan.cdc.app.cmd.CDCConfigCmd;
import com.cyan.cdc.app.service.CdcConfigQueryService;
import org.springframework.validation.annotation.Validated;
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
@RequestMapping("/cdc/config")
public class CdcConfigController {

    private final CdcConfigCmdService cdcConfigCmdService;
    private final CdcConfigQueryService cDCConfigQueryService;

    public CdcConfigController(CdcConfigCmdService cdcConfigCmdService, CdcConfigQueryService cDCConfigQueryService) {
        this.cdcConfigCmdService = cdcConfigCmdService;
        this.cDCConfigQueryService = cDCConfigQueryService;
    }

    /**
     * 查询cdc配置列表
     */
    @GetMapping("/list")
    public Response<List<CdcConfigDTO>> list(){
        List<CdcConfigBO> cdcConfigBOS =  cDCConfigQueryService.list();
        List<CdcConfigDTO> list = Optional.ofNullable(cdcConfigBOS).orElse(List.of()).stream().map(CdcConfigAdapterConvert.INSTANCE::toDatasourceInfoDTO).toList();
        return Response.success(list);
    }

    /**
     * 保存cdc配置信息
     */
    @PostMapping("/save")
    public Response<CdcConfigDTO> save(@RequestBody CDCConfigCmd cmd) {
        CdcConfigBO CDCConfigBO = cdcConfigCmdService.save(cmd);
        CdcConfigDTO CDCConfigDTO = CdcConfigAdapterConvert.INSTANCE.toDatasourceInfoDTO(CDCConfigBO);
        return Response.success(CDCConfigDTO);
    }

    /**
     * 启动cdc
     */
    @PostMapping("/start")
    public Response<Void> start(@RequestBody @Validated CDCStartCmd cmd) {
        cdcConfigCmdService.start(cmd);
        return Response.success();
    }

    /**
     * 关闭cdc
     */
    @PostMapping("/stop")
    public Response<Void> stop(@RequestBody @Validated CDCStartCmd cmd) {
        cdcConfigCmdService.stop(cmd);
        return Response.success();
    }
}
