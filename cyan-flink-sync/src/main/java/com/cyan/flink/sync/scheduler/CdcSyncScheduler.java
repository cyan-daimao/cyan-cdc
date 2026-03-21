package com.cyan.flink.sync.scheduler;

import com.cyan.flink.sync.config.CdcSyncConfig;
import com.cyan.flink.sync.config.CdcTableConfig;
import com.cyan.flink.sync.job.CdcSyncJob;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * CDC同步任务调度器
 * 
 * 核心设计：
 * 1. 按 connector_name 分组，一个 connector 一个 Flink Job
 * 2. DataStream API 实现，支持动态路由
 * 3. 一个 Job 内部路由多张表，增删表不需要重启 Job
 *
 * @author cy.Y
 * @since 1.0.0
 */
@Slf4j
public class CdcSyncScheduler {

    private final CdcSyncConfig config;
    private final CdcConfigMonitor configMonitor;
    private final FlinkJobSubmitter jobSubmitter;
    
    /**
     * 当前运行的任务: connectorName -> Flink Job ID
     */
    private final Map<String, String> runningJobs = new ConcurrentHashMap<>();

    public CdcSyncScheduler(CdcSyncConfig config) {
        this.config = config;
        this.configMonitor = new CdcConfigMonitor(config);
        this.jobSubmitter = new FlinkJobSubmitter(config);
    }

    /**
     * 启动调度器
     */
    public void start() {
        log.info("CDC同步调度器启动");
        
        if (!config.getMonitor().isEnabled()) {
            log.info("动态监控已禁用，只执行一次初始化");
            syncOnce();
            return;
        }

        while (!Thread.currentThread().isInterrupted()) {
            try {
                syncOnce();
                Thread.sleep(config.getMonitor().getIntervalMs());
            } catch (InterruptedException e) {
                log.info("调度器被中断，准备停止");
                break;
            } catch (Exception e) {
                log.error("同步任务执行异常: {}", e.getMessage(), e);
            }
        }
    }

    /**
     * 执行一次同步
     */
    public void syncOnce() {
        log.info("开始同步CDC配置");

        // 1. 按 connector 分组查询配置
        Map<String, List<CdcTableConfig>> groupedConfigs = configMonitor.queryGroupByConnector();

        // 2. 找出需要停止的 connector（整个 connector 都没有表了才停止）
        Set<String> toStop = new HashSet<>(runningJobs.keySet());
        toStop.removeAll(groupedConfigs.keySet());
        for (String connectorName : toStop) {
            stopJob(connectorName);
        }

        // 3. 对每个 connector 启动 Job（如果不存在）
        for (Map.Entry<String, List<CdcTableConfig>> entry : groupedConfigs.entrySet()) {
            String connectorName = entry.getKey();
            List<CdcTableConfig> tables = entry.getValue();

            if (!runningJobs.containsKey(connectorName)) {
                // Job 不存在，启动新 Job
                String jobId = startJob(connectorName, tables);
                if (jobId != null) {
                    runningJobs.put(connectorName, jobId);
                    configMonitor.updateFlinkJobId(connectorName, jobId);
                }
            } else {
                // Job 已存在，Job 内部会自动刷新表配置
                log.info("Connector {} 的 Job 已运行，Job ID: {}", connectorName, runningJobs.get(connectorName));
            }
        }

        log.info("同步完成，当前运行 Job 数: {}", runningJobs.size());
    }

    /**
     * 启动一个 connector 的同步 Job
     */
    private String startJob(String connectorName, List<CdcTableConfig> tables) {
        if (tables.isEmpty()) {
            return null;
        }

        try {
            log.info("启动 connector {} 的同步 Job，初始包含 {} 张表", connectorName, tables.size());

            // 构建 Job 并提交到 Flink
            String jobId = jobSubmitter.submitJob(connectorName, tables);
            
            if (jobId != null) {
                log.info("Connector {} 的 Job 启动成功，Job ID: {}", connectorName, jobId);
            }
            
            return jobId;
        } catch (Exception e) {
            log.error("启动同步 Job 失败: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 停止 Job
     */
    private void stopJob(String connectorName) {
        String jobId = runningJobs.remove(connectorName);
        if (jobId != null) {
            log.info("停止 connector {} 的 Job: {}", connectorName, jobId);
            jobSubmitter.stopJob(jobId);
            configMonitor.clearFlinkJobId(connectorName);
        }
    }

    /**
     * 停止所有任务
     */
    public void stopAll() {
        log.info("停止所有同步任务");
        for (String connectorName : new ArrayList<>(runningJobs.keySet())) {
            stopJob(connectorName);
        }
    }
}
