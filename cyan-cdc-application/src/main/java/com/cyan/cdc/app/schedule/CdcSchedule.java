package com.cyan.cdc.app.schedule;

import com.cyan.cdc.app.bo.CdcConfigBO;
import com.cyan.cdc.app.service.CdcConfigQueryService;
import com.cyan.cdc.app.service.CdcService;
import com.cyan.cdc.client.enums.RunningStatus;
import com.cyan.cdc.domain.query.CdcConfigListQuery;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * cdc调度器
 *
 * @author cy.Y
 * @since v1.0.0
 */
@Component
public class CdcSchedule {
    private final CdcConfigQueryService cdcConfigQueryService;
    private final CdcService cdcService;

    public CdcSchedule(CdcConfigQueryService cdcConfigQueryService, CdcService cdcService) {
        this.cdcConfigQueryService = cdcConfigQueryService;
        this.cdcService = cdcService;
    }

    //    @PostConstruct
    public void init() {
        //启动所有cdc任务
        List<CdcConfigBO> configBOS = cdcConfigQueryService.list(new CdcConfigListQuery().setRunningStatuses(List.of(RunningStatus.RUNNING)));
        for (CdcConfigBO configBO : configBOS) {
            cdcService.start(configBO.getId());
        }
    }
}
