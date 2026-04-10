package com.cyan.cdc.app.service.impl;

import com.cyan.arch.common.api.SilentException;
import com.cyan.arch.common.util.StrUtils;
import com.cyan.cdc.app.bo.CdcConfigBO;
import com.cyan.cdc.app.cmd.CDCConfigCmd;
import com.cyan.cdc.app.cmd.CDCStartCmd;
import com.cyan.cdc.app.cmd.CdcDeleteCmd;
import com.cyan.cdc.app.convert.CdcConfigAppConvert;
import com.cyan.cdc.app.service.CdcConfigCmdService;
import com.cyan.cdc.client.enums.RunningStatus;
import com.cyan.cdc.domain.CdcConfig;
import com.cyan.cdc.infra.convert.CdcConfigInfraConvert;
import com.cyan.cdc.infra.repository.CdcConfigRepository;
import com.cyan.cdc.infra.rpc.DebeziumRPC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 数据源信息服务
 *
 * @author cy.Y
 * @since 1.0.0
 */
@Service
@Transactional(rollbackFor = Exception.class)
public class CdcConfigCmdServiceImpl implements CdcConfigCmdService {

    @Value("${kafka.url}")
    private String kafkaUrl;

    private final CdcConfigRepository cdcConfigRepository;
    private final DebeziumRPC debeziumRPC;

    public CdcConfigCmdServiceImpl(CdcConfigRepository cdcConfigRepository, DebeziumRPC debeziumRPC) {
        this.cdcConfigRepository = cdcConfigRepository;
        this.debeziumRPC = debeziumRPC;
    }

    /**
     * 保存数据源信息
     */
    @Override
    @Transactional
    public CdcConfigBO save(CDCConfigCmd cmd) {
        CdcConfig cdcConfig = CdcConfigAppConvert.INSTANCE.toDatasourceInfo(cmd);
        cdcConfig = cdcConfig.save(cdcConfigRepository);
        return CdcConfigAppConvert.INSTANCE.toDatasourceInfoBO(cdcConfig);
    }

    /**
     * 更新cdc配置信息
     */
    @Override
    public void update(CDCConfigCmd cmd) {
        if (StrUtils.isBlank(cmd.getId())) {
            throw new SilentException("cdc-config id不能为空");
        }
    }

    /**
     * 启动cdc任务
     * 启用表并更新连接器配置，如果连接器未运行则启动它
     * <p>
     * 注意：当添加新表时，需要重启连接器让 Debezium 获取新表的 schema
     *
     * @param cmd 启动参数
     */
    @Override
    public void start(CDCStartCmd cmd) {
        CdcConfig cdcConfig = cdcConfigRepository.queryById(cmd.getId());
        if (cdcConfig == null) {
            throw new SilentException("cdc-config不存在");
        }

        // 获取该数据源下所有配置
        List<CdcConfig> allConfigs = cdcConfigRepository.listByDatasource(
                cdcConfig.getHostname(),
                cdcConfig.getPort(),
                cdcConfig.getUsername()
        );

        // 检查是否是新增表（之前没有启用的表，现在要启用）
        boolean isNewTable = !Boolean.TRUE.equals(cdcConfig.getEnabled());

        // 启用该表
        cdcConfig.setEnabled(true);
        cdcConfig.setRunningStatus(RunningStatus.RUNNING);
        cdcConfig.update(cdcConfigRepository);

        // 更新连接器配置
        String databaseIncludeList = CdcConfigInfraConvert.INSTANCE.buildDatabaseIncludeListEnabled(allConfigs);
        String tableIncludeList = CdcConfigInfraConvert.INSTANCE.buildTableIncludeListEnabled(allConfigs);
        debeziumRPC.updateConnector(cdcConfig.getConnectorName(),
                CdcConfigInfraConvert.INSTANCE.toMySQLConnectorConfig(cdcConfig, kafkaUrl, databaseIncludeList, tableIncludeList));

        // 如果是新增表，更新配置后重启连接器
        // 使用 incremental.snapshot.enabled=true 和 snapshot.mode=when_needed 时
        // Debezium 会自动对新表进行快照
        if (isNewTable) {
            try {
                // 先停止连接器
                debeziumRPC.stopConnector(cdcConfig.getConnectorName());
                // 等待一小段时间让连接器完全停止
                Thread.sleep(2000);
            } catch (Exception e) {
                // 忽略错误，连接器可能未在运行
            }

            // 启动连接器（配置已经在上面更新了）
            // Debezium 会检测到新表并自动进行快照
            try {
                debeziumRPC.startConnector(cdcConfig.getConnectorName());
            } catch (Exception e) {
                throw new SilentException("启动连接器失败：" + e.getMessage());
            }
        } else {
            // 不是新增表，直接启动连接器
            try {
                debeziumRPC.startConnector(cdcConfig.getConnectorName());
            } catch (Exception e) {
                // 连接器可能已经在运行，忽略错误
            }
        }
    }

    /**
     * 停止cdc任务
     * 禁用表并更新连接器配置
     *
     * @param cmd 停止参数
     */
    @Override
    public void stop(CDCStartCmd cmd) {
        CdcConfig cdcConfig = cdcConfigRepository.queryById(cmd.getId());
        if (cdcConfig == null) {
            throw new SilentException("cdc-config不存在");
        }
        
        // 禁用该表
        cdcConfig.setEnabled(false);
        cdcConfig.setRunningStatus(RunningStatus.STOP);
        cdcConfig.update(cdcConfigRepository);
        
        // 更新连接器配置（只包含启用的表）
        updateConnectorConfig(cdcConfig);
        
        // 检查该数据源下是否还有启用的表，如果没有则停止连接器
        List<CdcConfig> allConfigs = cdcConfigRepository.listByDatasource(
                cdcConfig.getHostname(), 
                cdcConfig.getPort(), 
                cdcConfig.getUsername()
        );
        boolean hasEnabledTable = allConfigs.stream()
                .anyMatch(c -> Boolean.TRUE.equals(c.getEnabled()));
        
        if (!hasEnabledTable) {
            try {
                debeziumRPC.stopConnector(cdcConfig.getConnectorName());
            } catch (Exception e) {
                // 忽略错误
            }
        }
    }

    /**
     * 更新cdc任务状态
     *
     * @param id     cdc任务id
     * @param status 运行状态
     * @param msg    运行信息
     */
    @Override
    public void updateStatus(String id, RunningStatus status, String msg) {
        CdcConfig cdcConfig = cdcConfigRepository.queryById(id);
        cdcConfig.setRunningStatus(status)
                .setMsg(msg);
        cdcConfig.update(cdcConfigRepository);
    }

    /**
     * 删除cdc任务
     * 删除表配置时，更新连接器的table.include.list
     * 如果该数据源下没有其他表了才删除连接器
     */
    @Override
    public void delete(CdcDeleteCmd cmd) {
        CdcConfig cdcConfig = cdcConfigRepository.queryById(cmd.getId());
        if (cdcConfig == null) {
            throw new SilentException("cdc-config不存在");
        }
        
        String connectorName = cdcConfig.getConnectorName();
        String hostname = cdcConfig.getHostname();
        String port = cdcConfig.getPort();
        String username = cdcConfig.getUsername();
        
        // 先删除数据库记录
        cdcConfigRepository.delete(cmd.getId());
        
        // 查询该数据源下剩余的配置
        List<CdcConfig> remainingConfigs = cdcConfigRepository.listByDatasource(hostname, port, username);
        
        if (remainingConfigs.isEmpty()) {
            // 该数据源下没有其他表了，删除连接器
            debeziumRPC.deleteConnector(connectorName);
        } else {
            // 还有其他表，更新连接器的table.include.list
            String databaseIncludeList = CdcConfigInfraConvert.INSTANCE.buildDatabaseIncludeList(remainingConfigs);
            String tableIncludeList = CdcConfigInfraConvert.INSTANCE.buildTableIncludeList(remainingConfigs);
            
            // 使用第一个配置作为基础配置
            CdcConfig baseConfig = remainingConfigs.getFirst();
            debeziumRPC.updateConnector(connectorName, 
                    CdcConfigInfraConvert.INSTANCE.toMySQLConnectorConfig(baseConfig, kafkaUrl, databaseIncludeList, tableIncludeList));
        }
    }

    /**
     * 更新连接器配置
     * 只包含启用状态的表
     *
     * @param cdcConfig cdc配置
     */
    private void updateConnectorConfig(CdcConfig cdcConfig) {
        // 获取该数据源下所有配置
        List<CdcConfig> allConfigs = cdcConfigRepository.listByDatasource(
                cdcConfig.getHostname(), 
                cdcConfig.getPort(), 
                cdcConfig.getUsername()
        );
        
        // 只包含启用状态的表
        String databaseIncludeList = CdcConfigInfraConvert.INSTANCE.buildDatabaseIncludeListEnabled(allConfigs);
        String tableIncludeList = CdcConfigInfraConvert.INSTANCE.buildTableIncludeListEnabled(allConfigs);
        
        // 更新连接器配置
        debeziumRPC.updateConnector(cdcConfig.getConnectorName(), 
                CdcConfigInfraConvert.INSTANCE.toMySQLConnectorConfig(cdcConfig, kafkaUrl, databaseIncludeList, tableIncludeList));
    }
}
