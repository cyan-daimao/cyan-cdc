package com.cyan.flink.sync;

import com.cyan.flink.sync.config.CdcSyncConfig;
import com.cyan.flink.sync.config.CdcTableConfig;
import com.cyan.flink.sync.job.CdcSyncJob;
import com.cyan.flink.sync.scheduler.CdcConfigMonitor;
import com.cyan.flink.sync.scheduler.CdcSyncScheduler;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

/**
 * CDC同步应用程序主入口
 * 
 * 运行方式:
 * 1. 作为 Flink Job 运行（从数据库加载配置，动态路由）:
 *    flink run -c com.cyan.flink.sync.CdcSyncApplication cyan-flink-sync.jar --mode job
 * 
 * 2. 作为调度器运行（监控cdc_config表，动态提交Job）:
 *    java -jar cyan-flink-sync.jar --mode scheduler
 *
 * @author cy.Y
 * @since 1.0.0
 */
public class CdcSyncApplication {

    private static final Logger log = LoggerFactory.getLogger(CdcSyncApplication.class);

    public static void main(String[] args) throws Exception {
        log.info("CDC同步应用程序启动");

        String mode = getArg(args, "--mode", "job");
        String configPath = getArg(args, "--config", null);

        CdcSyncConfig config = loadConfig(configPath);
        log.info("配置加载完成");

        if ("scheduler".equals(mode)) {
            // 调度器模式：监控 cdc_config，为每个 connector 提交 Job
            runAsScheduler(config);
        } else {
            // Job 模式：从数据库加载配置，动态路由（不需要 connector 参数）
            runAsFlinkJob(config);
        }
    }

    /**
     * 作为 Flink Job 运行
     * 从数据库加载所有表配置，根据 topic 动态路由到不同 Iceberg 表
     */
    private static void runAsFlinkJob(CdcSyncConfig config) throws Exception {
        log.info("作为 Flink Job 运行，从数据库加载配置");

        // 1. 从数据库查询所有运行中的表配置
        CdcConfigMonitor monitor = new CdcConfigMonitor(config);
        List<CdcTableConfig> tables = monitor.queryRunningConfigs();

        if (tables == null || tables.isEmpty()) {
            log.error("没有找到任何运行中的表配置");
            return;
        }

        log.info("找到 {} 张表需要同步", tables.size());

        // 2. 获取所有 connector（用于构建 topic pattern）
        Map<String, List<CdcTableConfig>> grouped = monitor.queryGroupByConnector();
        log.info("涉及 {} 个 connector: {}", grouped.keySet().size(), grouped.keySet());

        // 3. 构建 Flink Job
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(config.getFlink().getParallelism());
        env.enableCheckpointing(config.getFlink().getCheckpointInterval());

        // 本地调试时增加超时时间
        env.getCheckpointConfig().setCheckpointTimeout(600000); // 10分钟
        env.getCheckpointConfig().setTolerableCheckpointFailureNumber(3);

        // 4. 创建同步 Job（不指定 connector，处理所有表）
        CdcSyncJob job = new CdcSyncJob(config, tables);
        job.buildJob(env);

        // 5. 执行
        env.execute("cdc-sync-all");
    }

    /**
     * 作为调度器运行
     */
    private static void runAsScheduler(CdcSyncConfig config) {
        log.info("作为调度器运行");

        CdcSyncScheduler scheduler = new CdcSyncScheduler(config);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("应用程序关闭中...");
            scheduler.stopAll();
        }));

        scheduler.start();

        log.info("调度器已退出");
    }

    private static CdcSyncConfig loadConfig(String configPath) {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        
        try {
            if (configPath != null) {
                File configFile = new File(configPath);
                if (configFile.exists()) {
                    return mapper.readValue(configFile, CdcSyncConfig.class);
                }
            }

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

    private static String getArg(String[] args, String key, String defaultValue) {
        for (int i = 0; i < args.length - 1; i++) {
            if (key.equals(args[i])) {
                return args[i + 1];
            }
        }
        return defaultValue;
    }
}
