package kz.vivamind.storage;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;

import java.net.URI;

/**
 * Файлы в S3-совместимом хранилище (локально — SeaweedFS).
 * Сейчас здесь лежат исходные PDF курса, в фазе 2 — аудиозаписи ответов.
 */
@Service
public class StorageService {

    private static final Logger log = LoggerFactory.getLogger(StorageService.class);

    private final StorageProperties props;
    private final S3Client s3;

    public StorageService(StorageProperties props) {
        this.props = props;
        this.s3 = S3Client.builder()
                .endpointOverride(URI.create(props.endpoint()))
                .region(Region.of(props.region()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(props.accessKey(), props.secretKey())))
                .forcePathStyle(true)
                // Новые версии AWS SDK по умолчанию шлют дополнительные контрольные суммы,
                // которые понимают не все S3-совместимые хранилища.
                .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
                .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
                .build();
    }

    /** Бакет создаёт сам SeaweedFS (флаг -bucket). Здесь — подстраховка, если его всё-таки нет. */
    @PostConstruct
    void ensureBucket() {
        try {
            s3.headBucket(b -> b.bucket(props.bucket()));
        } catch (NoSuchBucketException e) {
            s3.createBucket(b -> b.bucket(props.bucket()));
            log.info("Created S3 bucket {}", props.bucket());
        } catch (RuntimeException e) {
            log.warn("S3 storage is not reachable at {} ({}). File upload will fail until it is up.",
                    props.endpoint(), e.getClass().getSimpleName());
        }
    }

    public void put(String key, byte[] content, String contentType) {
        s3.putObject(b -> b.bucket(props.bucket()).key(key).contentType(contentType),
                RequestBody.fromBytes(content));
    }

    public byte[] get(String key) {
        return s3.getObjectAsBytes(b -> b.bucket(props.bucket()).key(key)).asByteArray();
    }

    public void delete(String key) {
        s3.deleteObject(b -> b.bucket(props.bucket()).key(key));
    }

    @PreDestroy
    void close() {
        s3.close();
    }
}
