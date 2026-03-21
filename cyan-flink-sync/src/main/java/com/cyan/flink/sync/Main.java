package com.cyan.flink.sync;

import com.cy.easyhttp.HttpClientProxyFactory;
import com.cyan.flink.sync.config.CdcSyncConfig;
import com.cyan.flink.sync.rpc.CdcConfigDTO;
import com.cyan.flink.sync.rpc.CyanCdcRPC;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.List;

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
        //获得需要同的cdc配置列表
        CyanCdcRPC cyanCdcRPC = HttpClientProxyFactory.create(CyanCdcRPC.class);
        List<CdcConfigDTO> configs = cyanCdcRPC.list().data();
        System.out.println();
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
