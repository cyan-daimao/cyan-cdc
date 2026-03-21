package com.cyan.flink.sync.job;

import com.cyan.flink.sync.config.CdcSyncConfig;
import com.cyan.flink.sync.config.CdcTableConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.functions.OpenContext;
import org.apache.flink.api.common.functions.RichFlatMapFunction;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.connector.kafka.source.reader.deserializer.KafkaRecordDeserializationSchema;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.util.Collector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * CDC 同步 Job
 * 
 * 核心功能：
 * 1. 动态监听 cdc_config 表，支持增删表无需重启
 * 2. 根据 topic 动态路由到不同 Iceberg 表
 * 3. 使用 Iceberg Java API 直接写入
 *
 * @author cy.Y
 * @since 1.0.0
 */
public class CdcSyncJob {

    private static final Logger log = LoggerFactory.getLogger(CdcSyncJob.class);

    private final CdcSyncConfig config;
    private final Map<String, CdcTableConfig> tableConfigMap;
    
    private static final String SIGNAL_TABLE = "debezium_cdc.signal";

    public CdcSyncJob(CdcSyncConfig config, List<CdcTableConfig> tables) {
        this.config = config;
        this.tableConfigMap = tables.stream()
                .collect(Collectors.toConcurrentMap(CdcTableConfig::getKafkaTopic, Function.identity()));
    }

    /**
     * 构建 Flink Job
     */
    public void buildJob(StreamExecutionEnvironment env) {
        // 1. 获取所有 connector 名称，构建 topic pattern
        Map<String, List<CdcTableConfig>> groupedByConnector = tableConfigMap.values().stream()
                .collect(Collectors.groupingBy(CdcTableConfig::getConnectorName));
        
        // 构建 topic pattern: connector1\..*,connector2\..*
        // 使用 .* 匹配该 connector 下的所有表（包括新增的表）
        String topicPattern = groupedByConnector.keySet().stream()
                .map(connector -> connector + "\\..*")
                .collect(Collectors.joining(","));
        
        log.info("Kafka topic pattern: {}", topicPattern);
        log.info("初始表数量: {}, connector 数量: {}", tableConfigMap.size(), groupedByConnector.size());

        // 2. Kafka Source - 使用动态 topic pattern
        KafkaSource<Tuple2<String, String>> kafkaSource = KafkaSource.<Tuple2<String, String>>builder()
                .setBootstrapServers(config.getKafka().getBootstrapServers())
                .setGroupId(config.getKafka().getGroupIdPrefix())
                .setTopicPattern(java.util.regex.Pattern.compile(topicPattern))
                .setStartingOffsets(OffsetsInitializer.earliest())
                .setDeserializer(new TopicValueDeserializer())
                .build();

        DataStream<Tuple2<String, String>> kafkaStream = env
                .fromSource(kafkaSource, WatermarkStrategy.noWatermarks(), "kafka-source")
                .uid("kafka-source");

        // 3. 解析 Debezium JSON，过滤 signal 表
        SingleOutputStreamOperator<DebeziumRecord> parsedStream = kafkaStream
                .flatMap(new DebeziumJsonParser())
                .name("parse-debezium-json")
                .uid("parse-debezium-json");

        // 4. 动态路由到不同 Iceberg 表（核心：定时刷新 cdc_config）
        DynamicTableRouter router = new DynamicTableRouter(tableConfigMap, config);
        
        parsedStream.process(router)
                .name("route-to-iceberg")
                .uid("route-to-iceberg");

        log.info("CdcSyncJob 构建完成");
    }

    /**
     * 自定义反序列化器：返回 (topic, value)
     */
    public static class TopicValueDeserializer implements KafkaRecordDeserializationSchema<Tuple2<String, String>> {
        
        private static final long serialVersionUID = 1L;

        @Override
        public void deserialize(org.apache.kafka.clients.consumer.ConsumerRecord<byte[], byte[]> record, Collector<Tuple2<String, String>> out) throws IOException {
            String topic = record.topic();
            String value = record.value() != null ? new String(record.value(), java.nio.charset.StandardCharsets.UTF_8) : null;
            out.collect(new Tuple2<>(topic, value));
        }

        @Override
        public TypeInformation<Tuple2<String, String>> getProducedType() {
            return TypeInformation.of(new org.apache.flink.api.common.typeinfo.TypeHint<Tuple2<String, String>>() {});
        }
    }

    /**
     * Debezium JSON 解析器
     */
    public static class DebeziumJsonParser extends RichFlatMapFunction<Tuple2<String, String>, DebeziumRecord> {
        
        private transient ObjectMapper objectMapper;

        @Override
        public void open(OpenContext openContext) throws Exception {
            objectMapper = new ObjectMapper();
        }

        @Override
        public void flatMap(Tuple2<String, String> tuple, Collector<DebeziumRecord> out) {
            try {
                String topic = tuple.f0;
                String value = tuple.f1;
                
                // 过滤 signal 表
                if (topic != null && topic.endsWith(SIGNAL_TABLE)) {
                    log.debug("跳过 signal topic: {}", topic);
                    return;
                }
                
                // 解析 Debezium JSON
                DebeziumRecord record = parseDebeziumJson(topic, value);
                if (record != null && record.op != null) {
                    log.debug("解析 Debezium 消息: topic={}, op={}", topic, record.op);
                    out.collect(record);
                }
            } catch (Exception e) {
                log.warn("解析 Debezium JSON 失败: {}", e.getMessage());
            }
        }

        private DebeziumRecord parseDebeziumJson(String topic, String json) {
            try {
                JsonNode root = objectMapper.readTree(json);
                
                // Debezium 格式: { "schema": {...}, "payload": { "op": "c", "after": {...}, ... } }
                JsonNode payload = root.path("payload");
                
                DebeziumRecord record = new DebeziumRecord();
                record.topic = topic;
                record.op = payload.has("op") ? payload.get("op").asText() : null;
                record.tsMs = payload.has("ts_ms") ? payload.get("ts_ms").asLong() : 0;
                
                // after/before 是 JSON 对象，保持原样
                JsonNode afterNode = payload.path("after");
                record.after = afterNode.isObject() ? afterNode.toString() : null;
                
                JsonNode beforeNode = payload.path("before");
                record.before = beforeNode.isObject() ? beforeNode.toString() : null;
                
                JsonNode sourceNode = payload.path("source");
                record.source = sourceNode.isObject() ? sourceNode.toString() : null;
                
                return record;
            } catch (Exception e) {
                log.warn("JSON 解析失败: {}", e.getMessage());
                return null;
            }
        }
    }

    /**
     * Debezium 记录
     */
    public static class DebeziumRecord {
        public String topic;
        public String after;
        public String before;
        public String op;  // c=insert, u=update, d=delete
        public long tsMs;
        public String source;
    }
}