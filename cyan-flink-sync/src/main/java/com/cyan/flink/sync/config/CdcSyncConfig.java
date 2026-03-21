package com.cyan.flink.sync.config;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;

/**
 * CDC同步配置
 *
 * @author cy.Y
 * @since 1.0.0
 */
public class CdcSyncConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    @JsonProperty("mysql")
    private MysqlConfig mysql = new MysqlConfig();

    @JsonProperty("kafka")
    private KafkaConfig kafka = new KafkaConfig();

    @JsonProperty("flink")
    private FlinkConfig flink = new FlinkConfig();

    @JsonProperty("s3")
    private S3Config s3 = new S3Config();

    @JsonProperty("iceberg")
    private IcebergConfig iceberg = new IcebergConfig();

    @JsonProperty("monitor")
    private MonitorConfig monitor = new MonitorConfig();

    // Getters and Setters
    public MysqlConfig getMysql() { return mysql; }
    public void setMysql(MysqlConfig mysql) { this.mysql = mysql; }
    
    public KafkaConfig getKafka() { return kafka; }
    public void setKafka(KafkaConfig kafka) { this.kafka = kafka; }
    
    public FlinkConfig getFlink() { return flink; }
    public void setFlink(FlinkConfig flink) { this.flink = flink; }
    
    public S3Config getS3() { return s3; }
    public void setS3(S3Config s3) { this.s3 = s3; }
    
    public IcebergConfig getIceberg() { return iceberg; }
    public void setIceberg(IcebergConfig iceberg) { this.iceberg = iceberg; }
    
    public MonitorConfig getMonitor() { return monitor; }
    public void setMonitor(MonitorConfig monitor) { this.monitor = monitor; }

    public static class MysqlConfig implements Serializable {
        private static final long serialVersionUID = 1L;
        private String hostname = "localhost";
        private int port = 3306;
        private String database = "cyan_cdc";
        private String username = "root";
        private String password = "";
        
        @JsonProperty("cdc-config-table")
        private String cdcConfigTable = "cdc_config";

        public String getHostname() { return hostname; }
        public void setHostname(String hostname) { this.hostname = hostname; }
        
        public int getPort() { return port; }
        public void setPort(int port) { this.port = port; }
        
        public String getDatabase() { return database; }
        public void setDatabase(String database) { this.database = database; }
        
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
        
        public String getCdcConfigTable() { return cdcConfigTable; }
        public void setCdcConfigTable(String cdcConfigTable) { this.cdcConfigTable = cdcConfigTable; }
    }

    public static class KafkaConfig implements Serializable {
        private static final long serialVersionUID = 1L;
        
        @JsonProperty("bootstrap-servers")
        private String bootstrapServers = "localhost:9092";
        
        @JsonProperty("group-id-prefix")
        private String groupIdPrefix = "flink-cdc-sync";

        public String getBootstrapServers() { return bootstrapServers; }
        public void setBootstrapServers(String bootstrapServers) { this.bootstrapServers = bootstrapServers; }
        
        public String getGroupIdPrefix() { return groupIdPrefix; }
        public void setGroupIdPrefix(String groupIdPrefix) { this.groupIdPrefix = groupIdPrefix; }
    }

    public static class FlinkConfig implements Serializable {
        private static final long serialVersionUID = 1L;
        
        @JsonProperty("job-manager-url")
        private String jobManagerUrl = "http://localhost:8081";
        
        @JsonProperty("sql-gateway-url")
        private String sqlGatewayUrl = "http://localhost:8083";
        
        private int parallelism = 1;
        
        @JsonProperty("checkpoint-interval")
        private long checkpointInterval = 60000;
        
        @JsonProperty("checkpoint-dir")
        private String checkpointDir = "s3://flink/checkpoints";

        public String getJobManagerUrl() { return jobManagerUrl; }
        public void setJobManagerUrl(String jobManagerUrl) { this.jobManagerUrl = jobManagerUrl; }
        
        public String getSqlGatewayUrl() { return sqlGatewayUrl; }
        public void setSqlGatewayUrl(String sqlGatewayUrl) { this.sqlGatewayUrl = sqlGatewayUrl; }
        
        public int getParallelism() { return parallelism; }
        public void setParallelism(int parallelism) { this.parallelism = parallelism; }
        
        public long getCheckpointInterval() { return checkpointInterval; }
        public void setCheckpointInterval(long checkpointInterval) { this.checkpointInterval = checkpointInterval; }
        
        public String getCheckpointDir() { return checkpointDir; }
        public void setCheckpointDir(String checkpointDir) { this.checkpointDir = checkpointDir; }
    }

    public static class S3Config implements Serializable {
        private static final long serialVersionUID = 1L;
        private String endpoint = "http://localhost:9000";
        
        @JsonProperty("access-key")
        private String accessKey = "minioadmin";
        
        @JsonProperty("secret-key")
        private String secretKey = "minioadmin";
        
        @JsonProperty("warehouse-bucket")
        private String warehouseBucket = "warehouse";
        
        private String region = "us-east-1";

        public String getEndpoint() { return endpoint; }
        public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
        
        public String getAccessKey() { return accessKey; }
        public void setAccessKey(String accessKey) { this.accessKey = accessKey; }
        
        public String getSecretKey() { return secretKey; }
        public void setSecretKey(String secretKey) { this.secretKey = secretKey; }
        
        public String getWarehouseBucket() { return warehouseBucket; }
        public void setWarehouseBucket(String warehouseBucket) { this.warehouseBucket = warehouseBucket; }
        
        public String getRegion() { return region; }
        public void setRegion(String region) { this.region = region; }
    }

    public static class IcebergConfig implements Serializable {
        private static final long serialVersionUID = 1L;
        
        @JsonProperty("catalog-type")
        private String catalogType = "rest";
        
        @JsonProperty("catalog-uri")
        private String catalogUri = "http://localhost:8181";
        
        @JsonProperty("catalog-name")
        private String catalogName = "iceberg";
        
        @JsonProperty("default-database")
        private String defaultDatabase = "cdc_db";
        
        @JsonProperty("rewrite-data-files-threshold")
        private long rewriteDataFilesThreshold = 512 * 1024 * 1024L;

        public String getCatalogType() { return catalogType; }
        public void setCatalogType(String catalogType) { this.catalogType = catalogType; }
        
        public String getCatalogUri() { return catalogUri; }
        public void setCatalogUri(String catalogUri) { this.catalogUri = catalogUri; }
        
        public String getCatalogName() { return catalogName; }
        public void setCatalogName(String catalogName) { this.catalogName = catalogName; }
        
        public String getDefaultDatabase() { return defaultDatabase; }
        public void setDefaultDatabase(String defaultDatabase) { this.defaultDatabase = defaultDatabase; }
        
        public long getRewriteDataFilesThreshold() { return rewriteDataFilesThreshold; }
        public void setRewriteDataFilesThreshold(long rewriteDataFilesThreshold) { this.rewriteDataFilesThreshold = rewriteDataFilesThreshold; }
    }

    public static class MonitorConfig implements Serializable {
        private static final long serialVersionUID = 1L;
        
        @JsonProperty("interval-ms")
        private long intervalMs = 30000;
        
        private boolean enabled = true;

        public long getIntervalMs() { return intervalMs; }
        public void setIntervalMs(long intervalMs) { this.intervalMs = intervalMs; }
        
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
    }
}