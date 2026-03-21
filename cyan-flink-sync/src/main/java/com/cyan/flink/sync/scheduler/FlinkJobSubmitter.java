package com.cyan.flink.sync.scheduler;

import com.cyan.flink.sync.config.CdcSyncConfig;
import com.cyan.flink.sync.config.CdcTableConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.time.Duration;
import java.util.List;

/**
 * Flink Job 提交器
 * 
 * 通过 Flink REST API 提交 JAR 包
 *
 * @author cy.Y
 * @since 1.0.0
 */
@Slf4j
public class FlinkJobSubmitter {

    private final String jobManagerUrl;
    private final CdcSyncConfig config;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    
    /**
     * 已上传的 JAR ID 缓存
     */
    private String cachedJarId;

    public FlinkJobSubmitter(CdcSyncConfig config) {
        this.config = config;
        this.jobManagerUrl = config.getFlink().getJobManagerUrl();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 提交 Job 到 Flink
     */
    public String submitJob(String connectorName, List<CdcTableConfig> tables) {
        try {
            // 1. 上传 JAR 包（只上传一次）
            String jarId = uploadJar();
            if (jarId == null) {
                log.error("上传 JAR 包失败");
                return null;
            }
            
            // 2. 运行 JAR 包，传入 connector 参数
            String runUrl = jobManagerUrl + "/jars/" + jarId + "/run";
            
            // 传递 connector 参数
            String programArgs = "--connector " + connectorName;
            
            String requestBody = String.format("{\"programArgs\": \"%s\"}", programArgs);
            
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(runUrl))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonNode json = objectMapper.readTree(response.body());
                String jobId = json.path("jobid").asText();
                log.info("Job 提交成功，Job ID: {}, connector: {}", jobId, connectorName);
                return jobId;
            } else {
                log.error("运行 JAR 失败, status: {}, body: {}", response.statusCode(), response.body());
                return null;
            }
        } catch (Exception e) {
            log.error("提交 Job 失败: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 上传 JAR 包到 Flink
     */
    private String uploadJar() {
        // 使用缓存，避免重复上传
        if (cachedJarId != null) {
            return cachedJarId;
        }
        
        try {
            // JAR 包路径
            String jarPath = System.getProperty("flink.jar.path", "cyan-flink-sync.jar");
            File jarFile = new File(jarPath);
            
            if (!jarFile.exists()) {
                // 尝试其他路径
                String[] possiblePaths = {
                    "cyan-flink-sync.jar",
                    "target/cyan-flink-sync.jar",
                    "/opt/flink/usrlib/cyan-flink-sync.jar"
                };
                for (String path : possiblePaths) {
                    jarFile = new File(path);
                    if (jarFile.exists()) {
                        break;
                    }
                }
            }
            
            if (!jarFile.exists()) {
                log.error("JAR 包不存在，请先打包: mvn clean package");
                return null;
            }
            
            log.info("上传 JAR 包: {}", jarFile.getAbsolutePath());
            
            String uploadUrl = jobManagerUrl + "/jars/upload";
            
            byte[] jarBytes = Files.readAllBytes(jarFile.toPath());
            String boundary = "----FlinkJarUpload" + System.currentTimeMillis();
            
            String body = "--" + boundary + "\r\n" +
                    "Content-Disposition: form-data; name=\"jarfile\"; filename=\"" + jarFile.getName() + "\"\r\n" +
                    "Content-Type: application/octet-stream\r\n\r\n";
            
            String footer = "\r\n--" + boundary + "--\r\n";
            
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            baos.write(body.getBytes());
            baos.write(jarBytes);
            baos.write(footer.getBytes());
            
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(uploadUrl))
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(baos.toByteArray()))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonNode json = objectMapper.readTree(response.body());
                String filename = json.path("filename").asText();
                cachedJarId = filename.substring(filename.lastIndexOf('/') + 1);
                log.info("JAR 包上传成功，JAR ID: {}", cachedJarId);
                return cachedJarId;
            } else {
                log.error("上传 JAR 失败, status: {}, body: {}", response.statusCode(), response.body());
                return null;
            }
        } catch (Exception e) {
            log.error("上传 JAR 包失败: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 停止 Job
     */
    public void stopJob(String jobId) {
        try {
            String stopUrl = jobManagerUrl + "/jobs/" + jobId + "/stop";
            
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(stopUrl))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("{\"drain\": false}"))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 200 || response.statusCode() == 202) {
                log.info("已停止 Job: {}", jobId);
            } else {
                log.warn("停止 Job 返回非预期状态: {}, body: {}", response.statusCode(), response.body());
            }
        } catch (Exception e) {
            log.error("停止 Job 失败: {}", e.getMessage(), e);
        }
    }
    
    /**
     * 获取 Job 状态
     */
    public String getJobStatus(String jobId) {
        try {
            String statusUrl = jobManagerUrl + "/jobs/" + jobId;
            
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(statusUrl))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonNode json = objectMapper.readTree(response.body());
                return json.path("state").asText();
            }
        } catch (Exception e) {
            log.error("获取 Job 状态失败: {}", e.getMessage(), e);
        }
        return null;
    }
}