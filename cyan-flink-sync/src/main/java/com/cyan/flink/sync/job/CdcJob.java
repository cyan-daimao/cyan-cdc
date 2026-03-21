package com.cyan.flink.sync.job;

import com.alibaba.fastjson2.JSONObject;
import com.cyan.flink.sync.config.CdcSyncConfig;
import com.cyan.flink.sync.rpc.CdcConfigDTO;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.streaming.api.datastream.DataStreamSource;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;

import java.util.Properties;

/**
 * cdc同步任务
 *
 * @author cy.Y
 * @since 1.0.0
 */
public class CdcJob {

    private final StreamExecutionEnvironment env;
    /**
     * 配置文件
     */
    private final CdcSyncConfig config;
    /**
     * cdc配置
     */
    private final CdcConfigDTO cdcConfig;

    public CdcJob(StreamExecutionEnvironment env, CdcSyncConfig config, CdcConfigDTO cdcConfig) {
        this.env = env;
        this.config = config;
        this.cdcConfig = cdcConfig;
    }

    public void run() {
        KafkaSource<String> kafkaSource = createKafkaSource(config.getKafka().getBootstrapServers(), cdcConfig.getTopic(), config.getKafka().getGroupIdPrefix(), "earliest");
        String name = "%s.%s.%s".formatted(cdcConfig.getName(), cdcConfig.getDb(), cdcConfig.getTbl());
        DataStreamSource<String> stream = env.fromSource(kafkaSource, WatermarkStrategy.noWatermarks(), name);
        stream.name(name).map(v -> {
            DebeziumEventRecord debeziumEventRecord = JSONObject.parseObject(v, DebeziumEventRecord.class);
            //获得表字段
            DebeziumEventRecord.Field[] fields = debeziumEventRecord.getFields();
            //查询对应的iceberg表是否存在如果不存在就创建出来,自动创建要写以下属性
            /*
            -- Iceberg 写入优化配置
               -- 1. 实时性配置（核心：保证单条数据快速提交）
        'streaming.commit-interval' = '10000', -- 10秒超时提交（哪怕只有1条数据，10秒后也提交）
        'write.commit.manifest.min-count' = '100', -- 最小条目数设为1，允许单文件提交
        'write.filesize' = '67108864', -- 临时小文件大小：64MB（适配低流量）

        -- 2. 后台合并配置（核心：把小文件合并成512MB）
        'write.merge.enabled' = 'true', -- 开启写入时合并
        'write.merge.target-file-size-bytes' = '536870912', -- 最终合并为512MB
        'write.merge.sort-buffer-size' = '134217728', -- 128MB排序缓存，提升合并效率

        -- 3. 元数据保护配置（避免合并导致metadata膨胀）
        'metadata.delete-after-commit.enabled' = 'true',
        'metadata.previous-versions-max' = '5', -- 只保留最近5个版本
        'metadata.expire.snapshots.max-age-ms' = '3600000', -- 快照保留1小时
        'write.async' = 'true' -- 异步提交，不阻塞实时写入

            */
            // 往iceberg表写入数据
            System.out.println(v);
            return "[%s]:%s".formatted(name, v);
        }).print();

    }

    /**
     * 封装KafkaSource创建逻辑，避免重复代码
     *
     * @param brokers     Kafka broker地址
     * @param topic       要消费的Topic
     * @param groupId     消费组ID
     * @param offsetReset 偏移量重置策略（latest/earliest）
     * @return 配置好的KafkaSource
     */
    private static KafkaSource<String> createKafkaSource(
            String brokers,
            String topic,
            String groupId,
            String offsetReset) {

        // 配置Kafka消费者参数
        Properties kafkaProps = new Properties();
        kafkaProps.setProperty("bootstrap.servers", brokers);
        kafkaProps.setProperty("group.id", groupId);
        // 可选：添加更多消费参数（如超时、重试等）
        kafkaProps.setProperty("fetch.max.wait.ms", "500");
        kafkaProps.setProperty("max.poll.records", "100");

        // 构建KafkaSource
        KafkaSource<String> kafkaSource = KafkaSource.<String>builder()
                .setProperties(kafkaProps)
                .setTopics(topic)
                // 设置偏移量初始值
                .setStartingOffsets(
                        "latest".equals(offsetReset) ?
                                OffsetsInitializer.latest() :
                                OffsetsInitializer.earliest()
                )
                .setValueOnlyDeserializer(new SimpleStringSchema())  // 字符串反序列化（可替换为自定义Schema）
                .build();

        return kafkaSource;
    }
}
