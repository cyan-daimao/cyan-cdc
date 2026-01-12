package com.cyan.cdc.client.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 运行状态
 *
 * @author cy.Y
 * @since 1.0.0
 */
@Getter
@AllArgsConstructor
public enum RunningStatus {

    INIT("INIT"),
    RUNNING("RUNNING"),
    STOP("STOP"),
    SUCCESS("SUCCESS"),
    ERROR("ERROR"),
    ;

    private final String code;

    public static RunningStatus getByCode(String code) {
        for (RunningStatus value : values()) {
            if (value.code.equals(code)) {
                return value;
            }
        }
        return null;
    }
}
