package com.wangzhi.knowledgebase.storage;

import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.errors.ErrorResponseException;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Service
@ConditionalOnProperty(name = "app.storage.provider", havingValue = "minio")
public class MinioObjectStorageService implements ObjectStorageService {

    private final MinioClient client;
    private final String bucket;

    public MinioObjectStorageService(@Value("${app.storage.minio.endpoint}") String endpoint,
                                     @Value("${app.storage.minio.access-key}") String accessKey,
                                     @Value("${app.storage.minio.secret-key}") String secretKey,
                                     @Value("${app.storage.minio.bucket:myrag-knowledge}") String bucket) {
        this.client = MinioClient.builder().endpoint(endpoint).credentials(accessKey, secretKey).build();
        this.bucket = bucket;
    }

    @PostConstruct
    void ensureBucket() throws Exception {
        if (!client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
            client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
        }
    }

    @Override
    public StoredObject store(MultipartFile file) throws IOException {
        try {
            String hash = digest(file);
            String key = "objects/%s/%s".formatted(hash.substring(0, 2), hash);
            boolean deduplicated = exists(key);
            if (!deduplicated) {
                try (InputStream input = file.getInputStream()) {
                    client.putObject(PutObjectArgs.builder()
                            .bucket(bucket)
                            .object(key)
                            .stream(input, file.getSize(), -1L)
                            .contentType(file.getContentType())
                            .build());
                }
            }
            return new StoredObject(key, hash, file.getSize(), deduplicated);
        } catch (IOException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IOException("MinIO 对象写入失败", exception);
        }
    }

    @Override
    public InputStream open(String key) throws IOException {
        try {
            return client.getObject(GetObjectArgs.builder().bucket(bucket).object(key).build());
        } catch (Exception exception) {
            throw new IOException("MinIO 对象读取失败：" + key, exception);
        }
    }

    private boolean exists(String key) throws Exception {
        try {
            client.statObject(StatObjectArgs.builder().bucket(bucket).object(key).build());
            return true;
        } catch (ErrorResponseException exception) {
            if (exception.errorResponse() != null && "NoSuchKey".equals(exception.errorResponse().code())) {
                return false;
            }
            throw exception;
        }
    }

    private String digest(MultipartFile file) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前 JRE 不支持 SHA-256", exception);
        }
        try (InputStream input = new DigestInputStream(file.getInputStream(), digest)) {
            input.transferTo(OutputStream.nullOutputStream());
        }
        return HexFormat.of().formatHex(digest.digest());
    }
}
