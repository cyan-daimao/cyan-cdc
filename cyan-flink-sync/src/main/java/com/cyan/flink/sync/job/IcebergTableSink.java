package com.cyan.flink.sync.job;

import com.cyan.flink.sync.config.CdcSyncConfig;
import org.apache.iceberg.*;
import org.apache.iceberg.catalog.Namespace;
import org.apache.iceberg.catalog.TableIdentifier;
import org.apache.iceberg.data.GenericAppenderFactory;
import org.apache.iceberg.data.GenericRecord;
import org.apache.iceberg.data.Record;
import org.apache.iceberg.flink.actions.Actions;
import org.apache.iceberg.io.*;
import org.apache.iceberg.rest.RESTCatalog;
import org.apache.iceberg.types.Types;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Iceberg 表写入器
 * 
 * 使用 Iceberg Java API 直接写入数据
 * - 使用 Parquet 格式
 * - 支持小文件合并
 *
 * @author cy.Y
 * @since 1.0.0
 */
public class IcebergTableSink implements Closeable {

    private static final Logger log = LoggerFactory.getLogger(IcebergTableSink.class);

    private final String fullTableName;
    private final CdcSyncConfig config;
    private final Table icebergTable;
    private final Schema schema;
    
    // 计数器
    private final AtomicLong recordCount = new AtomicLong(0);
    private final long fileSizeThreshold;
    private final int compactThreshold = 10000;
    
    // 写入相关
    private transient GenericAppenderFactory appenderFactory;
    private transient FileAppender<Record> currentAppender;
    private transient String currentFilePath;
    private transient int fileCount = 0;
    
    // 小文件合并
    private transient long lastCompactTime = 0;
    private static final long COMPACT_INTERVAL_MS = 5 * 60 * 1000; // 5 分钟

    public IcebergTableSink(String fullTableName, CdcSyncConfig config) {
        this.fullTableName = fullTableName;
        this.config = config;
        this.fileSizeThreshold = config.getIceberg().getRewriteDataFilesThreshold();
        
        // 初始化 Iceberg 表
        this.icebergTable = initIcebergTable();
        this.schema = icebergTable != null ? icebergTable.schema() : createDefaultSchema();
        
        if (icebergTable != null) {
            initAppenderFactory();
            log.info("IcebergTableSink 初始化完成: {}", fullTableName);
        }
    }

    /**
     * 初始化 Appender 工厂
     */
    private void initAppenderFactory() {
        this.appenderFactory = new GenericAppenderFactory(
                schema,
                PartitionSpec.unpartitioned(),
                null,
                null,
                null
        );
        appenderFactory.setAll(icebergTable.properties());
    }

    /**
     * 写入 Debezium 记录
     */
    public void write(CdcSyncJob.DebeziumRecord record) {
        if (icebergTable == null) {
            log.warn("Iceberg 表未初始化，跳过写入");
            return;
        }
        
        try {
            // 确保有写入器
            if (currentAppender == null) {
                startNewFile();
            }
            
            // 转换为 Iceberg Record
            Record icebergRecord = toIcebergRecord(record);
            
            // 写入
            currentAppender.add(icebergRecord);
            
            long count = recordCount.incrementAndGet();
            
            // 定期检查合并
            if (count % compactThreshold == 0) {
                checkAndCompact();
            }
            
            log.trace("写入表 {}: op={}, ts={}", fullTableName, record.op, record.tsMs);
            
        } catch (Exception e) {
            log.error("写入记录失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 检查并合并小文件
     */
    private void checkAndCompact() {
        long now = System.currentTimeMillis();
        if (now - lastCompactTime < COMPACT_INTERVAL_MS) {
            return;
        }
        
        try {
            // 先关闭当前文件
            closeCurrentFile();
            
            log.info("触发小文件合并: {}, 累计记录数: {}", fullTableName, recordCount.get());
            
            // 使用 Iceberg Flink Actions API 合并小文件
            Actions.forTable(icebergTable)
                    .rewriteDataFiles()
                    .targetSizeInBytes(fileSizeThreshold)
                    .execute();
            
            lastCompactTime = now;
            log.info("小文件合并完成: {}", fullTableName);
            
        } catch (Exception e) {
            log.warn("小文件合并失败: {}", e.getMessage());
        }
    }

    /**
     * 转换为 Iceberg Record
     */
    private Record toIcebergRecord(CdcSyncJob.DebeziumRecord record) {
        GenericRecord icebergRecord = GenericRecord.create(schema);
        icebergRecord.setField("after", record.after);
        icebergRecord.setField("before", record.before);
        icebergRecord.setField("op", record.op);
        icebergRecord.setField("ts_ms", record.tsMs);
        icebergRecord.setField("source", record.source);
        return icebergRecord;
    }

    /**
     * 初始化 Iceberg 表
     */
    private Table initIcebergTable() {
        try {
            CdcSyncConfig.IcebergConfig icebergConfig = config.getIceberg();
            CdcSyncConfig.S3Config s3Config = config.getS3();

            Map<String, String> catalogProps = new HashMap<>();
            catalogProps.put("uri", icebergConfig.getCatalogUri());
            catalogProps.put("io-impl", "org.apache.iceberg.aws.s3.S3FileIO");
            catalogProps.put("s3.endpoint", s3Config.getEndpoint());
            catalogProps.put("s3.access-key-id", s3Config.getAccessKey());
            catalogProps.put("s3.secret-access-key", s3Config.getSecretKey());
            catalogProps.put("s3.region", s3Config.getRegion());

            log.info("初始化 REST Catalog: uri={}", icebergConfig.getCatalogUri());

            RESTCatalog catalog = new RESTCatalog();
            catalog.initialize(icebergConfig.getCatalogName(), catalogProps);

            // 解析表名: catalog.db.table -> db.table
            String[] parts = fullTableName.split("\\.", 2);
            if (parts.length != 2) {
                log.error("无效的表名格式: {}", fullTableName);
                return null;
            }

            String catalogName = parts[0];
            String dbTableName = parts[1];
            String[] dbTableParts = dbTableName.split("\\.", 2);
            if (dbTableParts.length != 2) {
                log.error("无效的表名格式: {}", fullTableName);
                return null;
            }

            String db = dbTableParts[0];
            String table = dbTableParts[1];

            Namespace namespace = Namespace.of(db);
            if (!catalog.namespaceExists(namespace)) {
                catalog.createNamespace(namespace);
                log.info("创建 namespace: {}", db);
            }

            TableIdentifier tableId = TableIdentifier.of(namespace, table);
            if (catalog.tableExists(tableId)) {
                log.info("加载已存在的表: {}", tableId);
                return catalog.loadTable(tableId);
            } else {
                Schema schema = createDefaultSchema();
                PartitionSpec partitionSpec = PartitionSpec.unpartitioned();
                log.info("创建新表: {}", tableId);
                return catalog.createTable(tableId, schema, partitionSpec);
            }
        } catch (Exception e) {
            log.error("初始化 Iceberg 表失败: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 开始新文件
     */
    private void startNewFile() {
        try {
            // 关闭当前文件
            closeCurrentFile();
            
            // 创建新文件路径
            String fileName = "data-" + System.currentTimeMillis() + "-" + fileCount + ".parquet";
            currentFilePath = icebergTable.locationProvider().newDataLocation(fileName);
            
            // 使用 GenericAppenderFactory 创建 Appender
            OutputFile outputFile = icebergTable.io().newOutputFile(currentFilePath);
            currentAppender = appenderFactory.newAppender(outputFile, FileFormat.PARQUET);
            
            fileCount++;
            log.debug("开始新文件: {}, 文件数: {}", fullTableName, fileCount);
            
        } catch (Exception e) {
            log.error("创建新文件失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 关闭当前文件并提交
     */
    private void closeCurrentFile() {
        if (currentAppender != null) {
            try {
                currentAppender.close();
                
                // 创建 DataFile 并提交
                DataFile dataFile = DataFiles.builder(icebergTable.spec())
                        .withPath(currentFilePath)
                        .withFileSizeInBytes(currentAppender.length())
                        .withFormat(FileFormat.PARQUET)
                        .withRecordCount(recordCount.get())
                        .build();
                
                icebergTable.newAppend().appendFile(dataFile).commit();
                
                log.debug("关闭文件并提交: {}, 记录数: {}", fullTableName, recordCount.get());
            } catch (Exception e) {
                log.error("关闭文件失败: {}", e.getMessage(), e);
            } finally {
                currentAppender = null;
                currentFilePath = null;
            }
        }
    }

    /**
     * 创建默认 Schema
     */
    private Schema createDefaultSchema() {
        return new Schema(
                Types.NestedField.required(1, "after", Types.StringType.get()),
                Types.NestedField.optional(2, "before", Types.StringType.get()),
                Types.NestedField.optional(3, "op", Types.StringType.get()),
                Types.NestedField.optional(4, "ts_ms", Types.LongType.get()),
                Types.NestedField.optional(5, "source", Types.StringType.get())
        );
    }

    @Override
    public void close() throws IOException {
        try {
            // 关闭当前文件
            closeCurrentFile();
            
            // 最后一次合并
            checkAndCompact();
            
            log.info("关闭 IcebergTableSink: {}, 累计写入记录数: {}", fullTableName, recordCount.get());
            
        } catch (Exception e) {
            log.error("关闭写入器失败: {}", e.getMessage(), e);
        }
    }
}
