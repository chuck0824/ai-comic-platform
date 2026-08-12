package com.aicp.common.storage;

import com.aicp.common.storage.local.LocalObjectStorage;
import com.aicp.common.storage.minio.MinioObjectStorage;
import com.aicp.common.storage.oss.OssObjectStorage;
import com.aicp.common.storage.qiniu.QiniuObjectStorage;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(StorageProperties.class)
public class StorageConfiguration {

    @Bean
    public LocalObjectStorage localObjectStorage(StorageProperties properties) {
        return new LocalObjectStorage(properties.getLocal());
    }

    @Bean
    public MinioObjectStorage minioObjectStorage(StorageProperties properties) {
        return new MinioObjectStorage(properties.getMinio());
    }

    @Bean
    public OssObjectStorage ossObjectStorage(StorageProperties properties) {
        return new OssObjectStorage(properties.getOss());
    }

    @Bean
    public QiniuObjectStorage qiniuObjectStorage(StorageProperties properties) {
        return new QiniuObjectStorage(properties.getQiniu());
    }
}
