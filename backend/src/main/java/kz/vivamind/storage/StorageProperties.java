package kz.vivamind.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("vivamind.s3")
public record StorageProperties(String endpoint, String region, String accessKey, String secretKey, String bucket) {
}
