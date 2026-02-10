package com.cyan.cdc.domain.query;

import com.cyan.cdc.client.enums.RunningStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * cdc列表查询参数
 * @author cy.Y
 * @since 1.0.0
 */
@AllArgsConstructor
@NoArgsConstructor
@Data
@Accessors(chain = true)
public class CdcConfigListQuery {
    /**
     * 运行状态
     */
    private List<RunningStatus> runningStatuses;
}
