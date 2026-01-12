package com.cyan.cdc.client.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * @author cy.Y
 * @since 1.0.0
 */
@AllArgsConstructor
@Getter
public enum DatasourceType {

    MYSQL("MYSQL"),

    ORACLE("ORACLE"),

    POSTGRESQL("POSTGRESQL"),

    CLICKHOUSE("CLICKHOUSE"),

    KAFKA("KAFKA"),

    MONGODB("MONGODB"),

    HBASE("HBASE"),

    DORIS("DORIS");

    private final String code;

    public static DatasourceType getByCode(String code) {
        for (DatasourceType value : values()) {
            if (value.code.equals(code)) {
                return value;
            }
        }
        return null;
    }
}
