package com.cyan.flink.sync.job;

import com.alibaba.fastjson2.JSONObject;
import com.cyan.flink.sync.config.CdcSyncConfig;
import com.cyan.flink.sync.rpc.CdcConfigDTO;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.functions.RichMapFunction;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.streaming.api.datastream.DataStreamSource;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.table.data.GenericRowData;
import org.apache.flink.table.data.RowData;
import org.apache.flink.table.data.StringData;
import org.apache.flink.types.RowKind;
import org.apache.iceberg.PartitionSpec;
import org.apache.iceberg.Schema;
import org.apache.iceberg.Table;
import org.apache.iceberg.catalog.Catalog;
import org.apache.iceberg.catalog.Namespace;
import org.apache.iceberg.catalog.SupportsNamespaces;
import org.apache.iceberg.catalog.TableIdentifier;
import org.apache.iceberg.flink.TableLoader;
import org.apache.iceberg.flink.sink.FlinkSink;
import org.apache.iceberg.rest.RESTCatalog;
import org.apache.iceberg.types.Types;
import org.apache.kafka.clients.consumer.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.time.Duration;
import java.util.*;

/**
 * cdc同步任务
 *
 * @author cy.Y
 * @since 1.0.0
 */
public class CdcJob {

    private static final Logger log = LoggerFactory.getLogger(CdcJob.class);

    private final StreamExecutionEnvironment env;
    private final CdcSyncConfig config;
    private final CdcConfigDTO cdcConfig;

    public CdcJob(StreamExecutionEnvironment env, CdcSyncConfig config, CdcConfigDTO cdcConfig) {
        this.env = env;
        this.config = config;
        this.cdcConfig = cdcConfig;
    }

    public void run() {
        String jobName = "%s.%s.%s".formatted(cdcConfig.getName(), cdcConfig.getDb(), cdcConfig.getTbl());
        log.info("启动CDC同步任务: {}", jobName);

        // 1. 初始化 Iceberg Catalog
        Catalog icebergCatalog = createIcebergCatalog();
        TableIdentifier tableId = TableIdentifier.of(
                Namespace.of(cdcConfig.getDb()),
                cdcConfig.getTbl()
        );

        // 2. 确保 namespace 存在
        ensureNamespaceExists(icebergCatalog, tableId.namespace());

        // 3. 获取或创建表 Schema
        Schema icebergSchema = getOrCreateTableSchema(icebergCatalog, tableId);

        // 4. 创建 Kafka Source
        // 使用 committedOffsets 从上次提交的位置继续消费，避免重复
        KafkaSource<String> kafkaSource = createKafkaSource(
                config.getKafka().getBootstrapServers(),
                cdcConfig.getTopic(),
                config.getKafka().getGroupIdPrefix() + "-" + jobName,
                "earliest"  // 使用已提交的 offset
        );

        // 5. 从 Kafka 读取数据
        DataStreamSource<String> kafkaStream = env.fromSource(
                kafkaSource,
                WatermarkStrategy.noWatermarks(),
                "kafka-source-" + jobName
        );

        // 6. 解析 Debezium 事件并转换为 RowData
        SingleOutputStreamOperator<RowData> rowDataStream = kafkaStream
                .name("parse-debezium-" + jobName)
                .map(new DebeziumEventMapper(icebergSchema))
                .filter(Objects::nonNull)
                .name("filter-valid-rows-" + jobName);

        // 打印 rowDataStream 数据用于调试
        rowDataStream.print("RowData");

        // 7. 写入 Iceberg 表
        Table table = icebergCatalog.loadTable(tableId);
        
        Map<String, String> catalogProps = buildCatalogProperties();

        org.apache.hadoop.conf.Configuration hadoopConf = new org.apache.hadoop.conf.Configuration();
        org.apache.iceberg.flink.CatalogLoader catalogLoader =
                org.apache.iceberg.flink.CatalogLoader.custom(
                        config.getIceberg().getCatalogName(),
                        catalogProps,
                        hadoopConf,
                        "org.apache.iceberg.rest.RESTCatalog"
                );


        TableLoader tableLoader = TableLoader.fromCatalog(catalogLoader, tableId);

        FlinkSink.forRowData(rowDataStream)
                .tableLoader(tableLoader)
                .table(table)
                .writeParallelism(env.getParallelism())
                .append();

        log.info("CDC同步任务配置完成: {}", jobName);
        
        // 打印当前表的 snapshot 信息
        logSnapshots(table, jobName);
    }

    /**
     * 获取或创建表 Schema
     * 如果表存在，直接使用现有 Schema
     * 如果表不存在，从 Kafka 消费一条消息获取 Schema 并创建表
     */
    private Schema getOrCreateTableSchema(Catalog catalog, TableIdentifier tableId) {
        // 如果表已存在，直接使用现有 Schema
        if (catalog.tableExists(tableId)) {
            Schema schema = catalog.loadTable(tableId).schema();
            log.info("使用已存在的 Iceberg 表 Schema: {}", tableId);
            return schema;
        }

        // 表不存在，从 Kafka 消费一条消息获取 Schema
        log.info("表不存在，从 Kafka 获取 Schema: {}", tableId);
        
        Properties props = new Properties();
        props.setProperty(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, config.getKafka().getBootstrapServers());
        props.setProperty(ConsumerConfig.GROUP_ID_CONFIG, config.getKafka().getGroupIdPrefix() + "-schema-init");
        props.setProperty(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringDeserializer");
        props.setProperty(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringDeserializer");
        props.setProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.setProperty(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, "1");

        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(Collections.singletonList(cdcConfig.getTopic()));
            
            // 轮询获取第一条消息
            String firstMessage = null;
            int maxAttempts = 30; // 最多等待30秒
            for (int i = 0; i < maxAttempts && firstMessage == null; i++) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(1));
                for (ConsumerRecord<String, String> record : records) {
                    firstMessage = record.value();
                    log.info("获取到第一条 Kafka 消息: {}", firstMessage);
                    break;
                }
            }
            
            if (firstMessage == null) {
                log.warn("无法从 Kafka 获取消息，使用默认 Schema");
                return createDefaultSchema();
            }
            
            // 解析 Debezium 消息获取字段
            DebeziumEventRecord debeziumRecord = JSONObject.parseObject(firstMessage, DebeziumEventRecord.class);
            DebeziumEventRecord.Field[] fields = debeziumRecord.getFields();
            
            if (fields == null || fields.length == 0) {
                log.warn("无法从 Debezium 消息获取字段，使用默认 Schema");
                return createDefaultSchema();
            }
            
            // 从字段创建 Schema
            Schema schema = createSchemaFromFields(fields);
            
            // 创建表
            PartitionSpec partitionSpec = PartitionSpec.unpartitioned();
            Table table = catalog.createTable(tableId, schema, partitionSpec);
            log.info("创建 Iceberg 表: {}, Schema: {}", tableId, schema);
            
            // 设置表属性
            table.updateProperties()
                    .set("streaming.commit-interval", "10000")
                    .set("write.commit.manifest.min-count", "100")
                    .set("write.filesize", "67108864")
                    .set("write.merge.enabled", "true")
                    .set("write.merge.target-file-size-bytes", "536870912")
                    .set("write.merge.sort-buffer-size", "134217728")
                    .set("metadata.delete-after-commit.enabled", "true")
                    .set("metadata.previous-versions-max", "5")
                    .set("metadata.expire.snapshots.max-age-ms", "3600000")
                    .set("write.async", "true")
                    .commit();
            
            return schema;
            
        } catch (Exception e) {
            log.error("从 Kafka 获取 Schema 失败", e);
            throw new RuntimeException("获取 Schema 失败", e);
        }
    }

    /**
     * 从 Debezium 字段创建 Iceberg Schema
     */
    private Schema createSchemaFromFields(DebeziumEventRecord.Field[] fields) {
        List<Types.NestedField> nestedFields = new ArrayList<>();
        int fieldId = 1;
        
        for (DebeziumEventRecord.Field field : fields) {
            String fieldName = field.getField();
            if (fieldName == null) {
                continue;
            }
            
            Types.NestedField nestedField = convertToIcebergField(field, fieldId++);
            if (nestedField != null) {
                nestedFields.add(nestedField);
            }
        }
        
        return new Schema(nestedFields);
    }

    /**
     * 转换 Debezium 字段为 Iceberg 字段
     */
    private Types.NestedField convertToIcebergField(DebeziumEventRecord.Field field, int fieldId) {
        String fieldName = field.getField();
        String type = field.getType();
        boolean optional = field.isOptional();
        
        return switch (type.toLowerCase()) {
            case "string" -> optional ? 
                Types.NestedField.optional(fieldId, fieldName, Types.StringType.get()) :
                Types.NestedField.required(fieldId, fieldName, Types.StringType.get());
            case "int16" -> optional ?
                Types.NestedField.optional(fieldId, fieldName, Types.IntegerType.get()) :
                Types.NestedField.required(fieldId, fieldName, Types.IntegerType.get());
            case "int32" -> optional ?
                Types.NestedField.optional(fieldId, fieldName, Types.IntegerType.get()) :
                Types.NestedField.required(fieldId, fieldName, Types.IntegerType.get());
            case "int64" -> optional ?
                Types.NestedField.optional(fieldId, fieldName, Types.LongType.get()) :
                Types.NestedField.required(fieldId, fieldName, Types.LongType.get());
            case "float" -> optional ?
                Types.NestedField.optional(fieldId, fieldName, Types.FloatType.get()) :
                Types.NestedField.required(fieldId, fieldName, Types.FloatType.get());
            case "double" -> optional ?
                Types.NestedField.optional(fieldId, fieldName, Types.DoubleType.get()) :
                Types.NestedField.required(fieldId, fieldName, Types.DoubleType.get());
            case "boolean" -> optional ?
                Types.NestedField.optional(fieldId, fieldName, Types.BooleanType.get()) :
                Types.NestedField.required(fieldId, fieldName, Types.BooleanType.get());
            case "bytes" -> optional ?
                Types.NestedField.optional(fieldId, fieldName, Types.BinaryType.get()) :
                Types.NestedField.required(fieldId, fieldName, Types.BinaryType.get());
            default -> optional ?
                Types.NestedField.optional(fieldId, fieldName, Types.StringType.get()) :
                Types.NestedField.required(fieldId, fieldName, Types.StringType.get());
        };
    }

    /**
     * 创建默认 Schema
     */
    private Schema createDefaultSchema() {
        return new Schema(
                Types.NestedField.optional(1, "data", Types.StringType.get())
        );
    }

    /**
     * 确保 namespace 存在
     */
    private void ensureNamespaceExists(Catalog catalog, Namespace namespace) {
        try {
            if (catalog instanceof SupportsNamespaces supportsNamespaces) {
                if (!supportsNamespaces.namespaceExists(namespace)) {
                    supportsNamespaces.createNamespace(namespace);
                    log.info("创建 namespace: {}", namespace);
                }
            }
        } catch (Exception e) {
            log.warn("创建 namespace 失败，可能已存在: {}", namespace, e);
        }
    }

    /**
     * 打印表的 snapshot 信息
     */
    private void logSnapshots(Table table, String jobName) {
        try {
            var snapshots = table.snapshots();
            log.info("[{}] 表 {} 当前有 {} 个 snapshots", jobName, table.name(), snapshots.spliterator().estimateSize());
            for (var snapshot : snapshots) {
                log.info("[{}] Snapshot: id={}, timestamp={}, files={}, records={}", 
                    jobName, snapshot.snapshotId(), snapshot.timestampMillis(),
                    snapshot.summary().getOrDefault("added-data-files", "0"),
                    snapshot.summary().getOrDefault("added-records", "0"));
            }
            
            // 打印当前 snapshot
            var currentSnapshot = table.currentSnapshot();
            if (currentSnapshot != null) {
                log.info("[{}] 当前 snapshot: id={}, manifestList={}", 
                    jobName, currentSnapshot.snapshotId(), currentSnapshot.manifestListLocation());
            } else {
                log.info("[{}] 表暂无 snapshot", jobName);
            }
        } catch (Exception e) {
            log.warn("[{}] 获取 snapshot 信息失败: {}", jobName, e.getMessage());
        }
    }

    /**
     * Debezium 事件映射器（可序列化）
     */
    public static class DebeziumEventMapper extends RichMapFunction<String, RowData> implements Serializable {
        private static final long serialVersionUID = 1L;
        
        private final Schema schema;
        private transient org.slf4j.Logger log;
        
        public DebeziumEventMapper(Schema schema) {
            this.schema = schema;
        }

        @Override
        public void open(org.apache.flink.api.common.functions.OpenContext openContext) {
            this.log = org.slf4j.LoggerFactory.getLogger(DebeziumEventMapper.class);
        }

        @Override
        public RowData map(String json) {
            try {
                DebeziumEventRecord record = JSONObject.parseObject(json, DebeziumEventRecord.class);
                if (record == null) {
                    log.debug("record is null");
                    return null;
                }
                if (record.getPayload() == null) {
                    log.debug("payload is null");
                    return null;
                }

                DebeziumEventRecord.Payload payload = record.getPayload();
                String op = payload.getOp();
                log.debug("op: {}", op);

                DebeziumEventRecord.Field[] fields = record.getFields();
                if (fields == null || fields.length == 0) {
                    log.warn("fields is null or empty, schema.name: {}", 
                        record.getSchema() != null ? record.getSchema().getName() : "null");
                    return null;
                }
                log.debug("fields count: {}", fields.length);

                Map<String, Object> data = switch (op) {
                    case "c" -> payload.getAfter();
                    case "u" -> payload.getAfter();
                    case "d" -> payload.getBefore();
                    case "r" -> payload.getAfter();
                    default -> null;
                };

                if (data == null) {
                    log.debug("data is null for op: {}", op);
                    return null;
                }
                log.debug("data keys: {}", data.keySet());

                RowData rowData = createRowData(fields, data, op, schema);
                log.debug("created rowData: {}", rowData);
                return rowData;
            } catch (Exception e) {
                log.error("parse error: {}", e.getMessage(), e);
                return null;
            }
        }

        private RowData createRowData(DebeziumEventRecord.Field[] fields, Map<String, Object> data, String op, Schema schema) {
            List<Types.NestedField> schemaFields = schema.columns();
            GenericRowData rowData = new GenericRowData(schemaFields.size());

            rowData.setRowKind(switch (op) {
                case "c", "r" -> RowKind.INSERT;
                case "u" -> RowKind.UPDATE_AFTER;
                case "d" -> RowKind.DELETE;
                default -> RowKind.INSERT;
            });

            // 构建 字段名 -> 类型 的映射
            Map<String, String> fieldTypeMap = new HashMap<>();
            for (DebeziumEventRecord.Field field : fields) {
                if (field.getField() != null) {
                    fieldTypeMap.put(field.getField(), field.getType());
                }
            }

            // 按 Schema 字段顺序填充数据
            int index = 0;
            for (Types.NestedField schemaField : schemaFields) {
                String fieldName = schemaField.name();
                String fieldType = fieldTypeMap.get(fieldName);
                Object value = data.get(fieldName);
                rowData.setField(index++, convertValue(value, fieldType));
            }

            return rowData;
        }

        private Object convertValue(Object value, String type) {
            if (value == null) {
                return null;
            }

            if (type == null) {
                return StringData.fromString(value.toString());
            }

            return switch (type.toLowerCase()) {
                case "string" -> StringData.fromString(value.toString());
                case "int16", "int32" -> {
                    if (value instanceof Number num) {
                        yield num.intValue();
                    }
                    yield Integer.parseInt(value.toString());
                }
                case "int64" -> {
                    if (value instanceof Number num) {
                        yield num.longValue();
                    }
                    yield Long.parseLong(value.toString());
                }
                case "float" -> {
                    if (value instanceof Number num) {
                        yield num.floatValue();
                    }
                    yield Float.parseFloat(value.toString());
                }
                case "double" -> {
                    if (value instanceof Number num) {
                        yield num.doubleValue();
                    }
                    yield Double.parseDouble(value.toString());
                }
                case "boolean" -> {
                    if (value instanceof Boolean bool) {
                        yield bool;
                    }
                    yield Boolean.parseBoolean(value.toString());
                }
                case "bytes" -> {
                    if (value instanceof byte[] bytes) {
                        yield bytes;
                    }
                    yield value.toString().getBytes();
                }
                default -> StringData.fromString(value.toString());
            };
        }
    }

    private Catalog createIcebergCatalog() {
        RESTCatalog catalog = new RESTCatalog();
        Map<String, String> catalogProperties = buildCatalogProperties();
        catalog.initialize(config.getIceberg().getCatalogName(), catalogProperties);
        
        return catalog;
    }

    private Map<String, String> buildCatalogProperties() {
        Map<String, String> properties = new HashMap<>();
        
        properties.put("uri", config.getIceberg().getCatalogUri());
        properties.put("io-impl", "org.apache.iceberg.aws.s3.S3FileIO");
        properties.put("s3.endpoint", config.getS3().getEndpoint());
        properties.put("s3.access-key-id", config.getS3().getAccessKey());
        properties.put("s3.secret-access-key", config.getS3().getSecretKey());
        properties.put("s3.region", config.getS3().getRegion());
        properties.put("s3.path-style-access", "true");
        
        return properties;
    }

    private static KafkaSource<String> createKafkaSource(
            String brokers,
            String topic,
            String groupId,
            String offsetReset) {

        Properties kafkaProps = new Properties();
        kafkaProps.setProperty("bootstrap.servers", brokers);
        kafkaProps.setProperty("group.id", groupId);
        kafkaProps.setProperty("fetch.max.wait.ms", "500");
        kafkaProps.setProperty("max.poll.records", "100");
        // 启用自动提交 offset（作为备份，主要靠 checkpoint）
        kafkaProps.setProperty("enable.auto.commit", "true");
        kafkaProps.setProperty("auto.commit.interval.ms", "5000");
        // 当没有已提交 offset 时的行为
        kafkaProps.setProperty("auto.offset.reset", "earliest");

        // 根据配置决定起始位置
        OffsetsInitializer startingOffsets = switch (offsetReset.toLowerCase()) {
            case "latest" -> OffsetsInitializer.latest();
            case "earliest" -> OffsetsInitializer.earliest();
            default -> OffsetsInitializer.committedOffsets(OffsetResetStrategy.EARLIEST);
            // committedOffsets 参数：如果没有已提交的 offset，则从 earliest 开始
        };

        return KafkaSource.<String>builder()
                .setProperties(kafkaProps)
                .setTopics(topic)
                .setStartingOffsets(startingOffsets)
                .setValueOnlyDeserializer(new SimpleStringSchema())
                .build();
    }
}