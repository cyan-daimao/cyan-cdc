package com.cyan.flink.sync;

import com.cyan.flink.sync.config.CdcSyncConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;

/**
 * 使用flink读取kafka然后同步到iceberg表上
 *
 * @author cy.Y
 * @since 1.0.0
 */
public class Main {

    private static final Logger log = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        log.info("CDC同步应用程序启动");
        CdcSyncConfig cdcSyncConfig = loadConfig();
        start(cdcSyncConfig);
    }

    /**
     * 启动程序
     */
    private static void start(CdcSyncConfig cdcSyncConfig) {

    }

    /**
     * 加载配置
     */
    private static CdcSyncConfig loadConfig() {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());

        try {
            InputStream is = CdcSyncApplication.class.getClassLoader()
                    .getResourceAsStream("config.yaml");
            if (is != null) {
                return mapper.readValue(is, CdcSyncConfig.class);
            }

            log.warn("未找到配置文件，使用默认配置");
            return new CdcSyncConfig();

        } catch (Exception e) {
            log.error("加载配置失败: {}", e.getMessage(), e);
            return new CdcSyncConfig();
        }
    }
}
