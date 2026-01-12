package com.cyan.cdc.app.cmd;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * 开启cdc
 * @author cy.Y
 * @since 1.0.0
 */
@AllArgsConstructor
@NoArgsConstructor
@Data
@Accessors(chain = true)
public class CDCStartCmd {

    /**
     * cdc配置id
     */
    @NotBlank(message = "cdc配置id不能为空")
    private String id;
}
