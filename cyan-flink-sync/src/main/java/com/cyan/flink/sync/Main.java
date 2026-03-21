package com.cyan.flink.sync;

import com.alibaba.fastjson2.JSON;
import com.cy.easyhttp.HttpClientProxyFactory;
import com.cyan.flink.sync.config.CdcSyncConfig;
import com.cyan.flink.sync.job.CdcJob;
import com.cyan.flink.sync.rpc.CdcConfigDTO;
import com.cyan.flink.sync.rpc.CyanCdcRPC;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.List;
import java.util.Optional;

/**
 * 使用flink读取kafka然后同步到iceberg表上
 *
 * @author cy.Y
 * @since 1.0.0
 */
public class Main {

    private static final Logger log = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) throws Exception {
        log.info("CDC同步应用程序启动");
        CdcSyncConfig cdcSyncConfig = loadConfig();
        start(cdcSyncConfig);
    }

    /**
     * 启动程序
     */
    private static void start(CdcSyncConfig cdcSyncConfig) throws Exception {
        //获得需要同的cdc配置列表
        CyanCdcRPC cyanCdcRPC = HttpClientProxyFactory.create(CyanCdcRPC.class);
        List<CdcConfigDTO> configs = Optional.ofNullable(cyanCdcRPC.list().data()).orElse(List.of()).stream().filter(cdcConfigDTO -> cdcConfigDTO.getRunningStatus().equals("RUNNING")).toList();
        log.info("需要同步的cdc配置列表: {}", JSON.toJSONString(configs));

        // 3. 构建 Flink Job
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(cdcSyncConfig.getFlink().getParallelism());
        env.enableCheckpointing(cdcSyncConfig.getFlink().getCheckpointInterval());

        // 本地调试时增加超时时间
        env.getCheckpointConfig().setCheckpointTimeout(600000); // 10分钟
        env.getCheckpointConfig().setTolerableCheckpointFailureNumber(3);

        //创建对应的同步任务
        for (CdcConfigDTO config : configs) {
            CdcJob cdcJob = new CdcJob(env, cdcSyncConfig, config);
            cdcJob.run();
        }
        env.execute();
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
