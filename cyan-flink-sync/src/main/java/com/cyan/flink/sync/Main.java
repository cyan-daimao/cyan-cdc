package com.cyan.flink.sync;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.alibaba.fastjson2.JSON;
import com.cy.easyhttp.HttpClientProxyFactory;
import com.cyan.flink.sync.config.CdcSyncConfig;
import com.cyan.flink.sync.job.CdcJob;
import com.cyan.flink.sync.rpc.CdcConfigDTO;
import com.cyan.flink.sync.rpc.CyanCdcRPC;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
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

    private static final org.slf4j.Logger log = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) throws Exception {
        // 配置日志级别，关闭 DEBUG 日志
        configureLogging();
        
        log.info("CDC同步应用程序启动");
        CdcSyncConfig cdcSyncConfig = loadConfig();
        start(cdcSyncConfig);
    }

    /**
     * 配置日志级别
     */
    private static void configureLogging() {
        // 关闭 Kafka DEBUG 日志
        ((Logger) LoggerFactory.getLogger("org.apache.kafka")).setLevel(Level.WARN);
        // 关闭 Flink DEBUG 日志  
        ((Logger) LoggerFactory.getLogger("org.apache.flink")).setLevel(Level.INFO);
        // 关闭 Kryo 序列化警告
        ((Logger) LoggerFactory.getLogger("org.apache.flink.api.java.typeutils.runtime.kryo")).setLevel(Level.ERROR);
        // 关闭 Iceberg DEBUG 日志
        ((Logger) LoggerFactory.getLogger("org.apache.iceberg")).setLevel(Level.INFO);
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
        
        // 启用 checkpoint（Iceberg 写入必须依赖 checkpoint commit）
        long checkpointInterval = cdcSyncConfig.getFlink().getCheckpointInterval();
        long interval = checkpointInterval > 0 ? checkpointInterval : 10000; // 默认 10 秒
        env.enableCheckpointing(interval);
        log.info("Checkpoint 间隔: {}ms", interval);
        
        // 本地调试时增加超时时间
        env.getCheckpointConfig().setCheckpointTimeout(600000); // 10分钟
        env.getCheckpointConfig().setTolerableCheckpointFailureNumber(3);
        
        // 确保 checkpoint 完成
        env.getCheckpointConfig().setMinPauseBetweenCheckpoints(500);
        env.getCheckpointConfig().setMaxConcurrentCheckpoints(1);
        
        // 配置 exactly-once 语义
        env.getCheckpointConfig().setCheckpointingMode(org.apache.flink.streaming.api.CheckpointingMode.EXACTLY_ONCE);
        
        // 启用 unaligned checkpoint（加速）
        env.getCheckpointConfig().enableUnalignedCheckpoints();
        
        log.info("Checkpoint 配置完成");

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
            InputStream is = Main.class.getClassLoader()
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