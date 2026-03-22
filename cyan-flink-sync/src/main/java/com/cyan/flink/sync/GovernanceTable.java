package com.cyan.flink.sync;

import com.cyan.flink.sync.config.CdcSyncConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.apache.iceberg.Table;
import org.apache.iceberg.catalog.TableIdentifier;
import org.apache.iceberg.flink.actions.Actions;
import org.apache.iceberg.rest.RESTCatalog;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 治理表
 *
 * @author cy.Y
 * @since 1.0.0
 */
public class GovernanceTable {
    public static void main(String[] args) {
        CdcSyncConfig config = loadConfig();

        RESTCatalog catalog = new RESTCatalog();
        catalog.initialize(config.getIceberg().getCatalogName(),buildCatalogProperties(config));
        Table table = catalog.loadTable(TableIdentifier.of("cyan_employee", "cyan_department"));
        Actions.forTable(table).rewriteDataFiles().execute();
        table.expireSnapshots().expireOlderThan(System.currentTimeMillis()).commit();
    }

    private static Map<String, String> buildCatalogProperties(CdcSyncConfig config) {
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

            return new CdcSyncConfig();

        } catch (Exception e) {
            return new CdcSyncConfig();
        }
    }
}
