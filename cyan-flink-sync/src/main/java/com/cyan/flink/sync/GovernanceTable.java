package com.cyan.flink.sync;

import com.cyan.flink.sync.config.CdcSyncConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.apache.iceberg.*;
import org.apache.iceberg.catalog.TableIdentifier;
import org.apache.iceberg.flink.actions.Actions;
import org.apache.iceberg.io.FileIO;
import org.apache.iceberg.io.FileInfo;
import org.apache.iceberg.io.SupportsPrefixOperations;
import org.apache.iceberg.rest.RESTCatalog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.*;

/**
 * 治理表
 * 主要功能：
 * 1. 合并小文件 - RewriteDataFiles
 * 2. 过期快照 - ExpireSnapshots
 * 3. 清理孤立文件 - 删除不在当前快照中引用的文件
 *
 * @author cy.Y
 * @since 1.0.0
 */
public class GovernanceTable {
    private static final Logger LOG = LoggerFactory.getLogger(GovernanceTable.class);

    public static void main(String[] args) throws Exception {
        CdcSyncConfig config = loadConfig();

        // 初始化 Catalog 并加载表
        RESTCatalog catalog = new RESTCatalog();
        catalog.initialize(config.getIceberg().getCatalogName(), buildCatalogProperties(config));
        Table table = catalog.loadTable(TableIdentifier.of("cyan_employee", "cyan_department"));
        FileIO fileIO = table.io();

        // 打印当前快照信息
        Snapshot currentSnapshot = table.currentSnapshot();
        LOG.info("当前快照 ID: {}", currentSnapshot != null ? currentSnapshot.snapshotId() : "null");
        LOG.info("快照总数: {}", countSnapshots(table));

        // 1. 合并小文件
        LOG.info("开始合并小文件...");
        var rewriteResult = Actions.forTable(table).rewriteDataFiles().execute();
        LOG.info("小文件合并完成, 删除文件数: {}, 新增文件数: {}", 
                rewriteResult.deletedDataFiles().size(),
                rewriteResult.addedDataFiles().size());

        // 刷新表以获取最新状态
        table.refresh();
        LOG.info("合并后快照 ID: {}", table.currentSnapshot() != null ? table.currentSnapshot().snapshotId() : "null");

        // 2. 收集当前快照中引用的所有文件（有效文件）
        LOG.info("收集当前快照中的有效文件...");
        Set<String> validFiles = collectValidFiles(table);
        LOG.info("有效文件数量: {}", validFiles.size());
        validFiles.forEach(f -> LOG.debug("有效文件: {}", f));

        // 3. 过期快照（保留最后1个）
        LOG.info("开始过期快照...");
        table.expireSnapshots()
                .expireOlderThan(System.currentTimeMillis())
                .retainLast(1)
                .cleanExpiredFiles(true)
                .cleanExpiredMetadata(true)
                .commit();
        LOG.info("快照过期完成");

        // 刷新表
        table.refresh();
        LOG.info("过期后快照总数: {}", countSnapshots(table));

        // 4. 删除孤立文件（不在当前快照中引用的文件）
        LOG.info("开始清理孤立文件...");
        int deletedCount = deleteOrphanFiles(table, fileIO, validFiles);
        LOG.info("孤立文件清理完成, 删除文件数: {}", deletedCount);

        // 打印当前快照的数据文件
        LOG.info("当前快照数据文件:");
        for (FileScanTask task : table.newScan().planFiles()) {
            LOG.info("  数据文件: {}", task.file().path());
        }

        LOG.info("表治理完成!");
        catalog.close();
    }

    /**
     * 收集当前快照中引用的所有有效文件路径
     */
    private static Set<String> collectValidFiles(Table table) {
        Set<String> validFiles = new HashSet<>();

        // 1. 收集当前快照的数据文件
        for (FileScanTask task : table.newScan().planFiles()) {
            String path = task.file().path().toString();
            validFiles.add(path);
            LOG.debug("数据文件: {}", path);
        }

        // 2. 收集当前快照的 manifest 相关文件
        Snapshot currentSnapshot = table.currentSnapshot();
        if (currentSnapshot != null) {
            // manifest list 文件
            if (currentSnapshot.manifestListLocation() != null) {
                validFiles.add(currentSnapshot.manifestListLocation());
                LOG.debug("Manifest list: {}", currentSnapshot.manifestListLocation());
            }
            
            // manifest 文件
            List<ManifestFile> manifests = currentSnapshot.allManifests(table.io());
            for (ManifestFile manifest : manifests) {
                validFiles.add(manifest.path());
                LOG.debug("Manifest: {}", manifest.path());
            }
        }

        return validFiles;
    }

    /**
     * 删除孤立文件
     */
    private static int deleteOrphanFiles(Table table, FileIO fileIO, Set<String> validFiles) {
        if (!(fileIO instanceof SupportsPrefixOperations)) {
            LOG.warn("FileIO 不支持列出文件, 跳过孤立文件清理");
            return 0;
        }

        SupportsPrefixOperations prefixOps = (SupportsPrefixOperations) fileIO;
        String tableLocation = table.location();
        LOG.info("表位置: {}", tableLocation);

        int[] deletedCount = {0};

        // 清理 data 目录
        String dataLocation = tableLocation + "/data";
        LOG.info("扫描 data 目录: {}", dataLocation);
        listAndDeleteOrphans(prefixOps, dataLocation, validFiles, deletedCount, "data");

        // 清理 metadata 目录
        String metadataLocation = tableLocation + "/metadata";
        LOG.info("扫描 metadata 目录: {}", metadataLocation);
        listAndDeleteOrphans(prefixOps, metadataLocation, validFiles, deletedCount, "metadata");

        return deletedCount[0];
    }

    /**
     * 列出目录并删除孤立文件
     */
    private static void listAndDeleteOrphans(
            SupportsPrefixOperations fileIO, 
            String location, 
            Set<String> validFiles, 
            int[] deletedCount,
            String dirType) {
        try {
            Iterable<FileInfo> files = fileIO.listPrefix(location);
            List<String> toDelete = new ArrayList<>();
            
            // 收集所有 metadata.json 文件，找出最新版本
            List<String> metadataJsonFiles = new ArrayList<>();
            
            for (FileInfo fileInfo : files) {
                String filePath = fileInfo.location();
                
                if (filePath.endsWith(".metadata.json")) {
                    metadataJsonFiles.add(filePath);
                } else if (!isValidFile(filePath, validFiles)) {
                    // 非 metadata.json 文件，检查是否孤立
                    toDelete.add(filePath);
                    LOG.info("发现孤立文件 [{}]: {}", dirType, filePath);
                }
            }
            
            // 处理 metadata.json 文件：只保留版本号最大的那个
            if (!metadataJsonFiles.isEmpty()) {
                String latestMetadata = findLatestMetadataFile(metadataJsonFiles);
                LOG.info("最新 metadata 文件: {}", latestMetadata);
                
                for (String metaFile : metadataJsonFiles) {
                    if (!metaFile.equals(latestMetadata)) {
                        toDelete.add(metaFile);
                        LOG.info("发现旧版本 metadata 文件: {}", metaFile);
                    }
                }
            }
            
            // 删除孤立文件
            for (String filePath : toDelete) {
                try {
                    fileIO.deleteFile(filePath);
                    deletedCount[0]++;
                    LOG.info("已删除: {}", filePath);
                } catch (Exception e) {
                    LOG.warn("删除失败: {}, 错误: {}", filePath, e.getMessage());
                }
            }
            
            LOG.info("目录 {} 扫描完成, 发现孤立文件: {}, 已删除: {}", dirType, toDelete.size(), deletedCount[0]);
            
        } catch (Exception e) {
            LOG.error("扫描目录失败: {}, 错误: {}", location, e.getMessage());
        }
    }

    /**
     * 找出最新的 metadata.json 文件（版本号最大的）
     * 文件命名格式: 00000-xxx.metadata.json, 00001-xxx.metadata.json
     */
    private static String findLatestMetadataFile(List<String> metadataFiles) {
        String latest = null;
        int latestVersion = -1;
        
        for (String file : metadataFiles) {
            int version = extractMetadataVersion(file);
            if (version > latestVersion) {
                latestVersion = version;
                latest = file;
            }
        }
        
        return latest;
    }

    /**
     * 从 metadata.json 文件名中提取版本号
     * 例如: /path/00005-xxx.metadata.json -> 5
     */
    private static int extractMetadataVersion(String filePath) {
        try {
            // 获取文件名
            String fileName = filePath;
            int lastSlash = filePath.lastIndexOf('/');
            if (lastSlash >= 0) {
                fileName = filePath.substring(lastSlash + 1);
            }
            
            // 提取版本号 (文件名开头的数字)
            int dashIndex = fileName.indexOf('-');
            if (dashIndex > 0) {
                String versionStr = fileName.substring(0, dashIndex);
                return Integer.parseInt(versionStr);
            }
        } catch (Exception e) {
            LOG.warn("无法解析 metadata 版本号: {}", filePath);
        }
        return -1;
    }

    /**
     * 检查文件是否有效
     */
    private static boolean isValidFile(String filePath, Set<String> validFiles) {
        // 直接匹配
        if (validFiles.contains(filePath)) {
            return true;
        }
        
        // 规范化路径匹配
        String normalizedPath = normalizePath(filePath);
        for (String validFile : validFiles) {
            if (normalizePath(validFile).equals(normalizedPath)) {
                return true;
            }
        }
        
        return false;
    }

    /**
     * 规范化路径（移除 scheme 和 authority）
     */
    private static String normalizePath(String path) {
        if (path == null) return "";
        if (path.contains("://")) {
            int idx = path.indexOf("/", path.indexOf("://") + 3);
            if (idx > 0) {
                return path.substring(idx);
            }
        }
        return path;
    }

    private static int countSnapshots(Table table) {
        int count = 0;
        for (Snapshot s : table.snapshots()) {
            count++;
        }
        return count;
    }

    private static Map<String, String> buildCatalogProperties(CdcSyncConfig config) {
        Map<String, String> properties = new java.util.HashMap<>();

        properties.put(CatalogProperties.URI, config.getIceberg().getCatalogUri());
        properties.put("io-impl", "org.apache.iceberg.aws.s3.S3FileIO");
        properties.put("s3.endpoint", config.getS3().getEndpoint());
        properties.put("s3.access-key-id", config.getS3().getAccessKey());
        properties.put("s3.secret-access-key", config.getS3().getSecretKey());
        properties.put("s3.region", config.getS3().getRegion());
        properties.put("s3.path-style-access", "true");

        return properties;
    }

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
