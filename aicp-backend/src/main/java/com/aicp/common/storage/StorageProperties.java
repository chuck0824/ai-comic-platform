package com.aicp.common.storage;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@Data
@ConfigurationProperties(prefix = "storage")
public class StorageProperties {

    /** Active backend for new uploads: minio | oss | qiniu | local */
    private String provider = "local";

    private Duration signedUrlExpiry = Duration.ofSeconds(300);

    private Minio minio = new Minio();
    private Oss oss = new Oss();
    private Qiniu qiniu = new Qiniu();
    private Local local = new Local();

    @Data
    public static class Minio {
        private String endpoint = "http://localhost:9000";
        private String accessKey = "minioadmin";
        private String secretKey = "minioadmin123";
        private String bucket = "aicp-assets";
        /** Optional public base URL if behind a reverse proxy/CDN. */
        private String publicBaseUrl;
    }

    @Data
    public static class Oss {
        private String endpoint = "https://oss-cn-hangzhou.aliyuncs.com";
        private String accessKeyId = "";
        private String accessKeySecret = "";
        private String bucket = "aicp-assets";
        /** Optional CDN / custom domain, e.g. https://cdn.example.com */
        private String publicBaseUrl;
    }

    @Data
    public static class Qiniu {
        private String accessKey = "";
        private String secretKey = "";
        private String bucket = "aicp-assets";
        /** Upload region hostname, e.g. https://upload.qiniup.com */
        private String uploadUrl = "https://upload.qiniup.com";
        /** Public or private download domain, e.g. https://cdn.example.com */
        private String domain = "";
        private boolean useHttps = true;
    }

    @Data
    public static class Local {
        private String root = "./data/storage";
        private String bucket = "aicp-assets";
        private String publicBaseUrl = "http://localhost:8080/api/v1/storage/local";
    }
}
