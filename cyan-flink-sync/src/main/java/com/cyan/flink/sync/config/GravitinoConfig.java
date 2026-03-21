package com.cyan.flink.sync.config;

import org.apache.gravitino.Catalog;
import org.apache.gravitino.client.GravitinoClient;

/**
 * Gravitino配置
 * @author cy.Y
 * @since 1.0.0
 */
public class GravitinoConfig {

    /**
     * 获得Gravitino客户端
     * @return GravitinoClient
     */
    public static GravitinoClient gravitinoClient(){
        return GravitinoClient.builder("http://gravitino.cyan.com")
                .withMetalake("cyan")
                .build();
    }

    public static Catalog catalog(){
        return gravitinoClient().loadCatalog("iceberg");
    }
}
