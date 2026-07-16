package com.wangzhi.knowledgebase.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

@Service
@ConditionalOnProperty(name = "app.storage.provider", havingValue = "filesystem", matchIfMissing = true)
public class ContentAddressedFileStorageService implements ObjectStorageService {

    private final Path root;

    public ContentAddressedFileStorageService(
            @Value("${app.storage.root:${app.import.storage-path:./data/imports}}") String storagePath) {
        this.root = Path.of(storagePath).toAbsolutePath().normalize();
    }

    @Override
    public StoredObject store(MultipartFile file) throws IOException {
        Path temporaryDirectory = root.resolve(".tmp");
        Files.createDirectories(temporaryDirectory);
        Path temporary = temporaryDirectory.resolve(UUID.randomUUID() + ".upload");
        MessageDigest digest = sha256();
        long copied;
        try (InputStream input = new DigestInputStream(file.getInputStream(), digest)) {
            copied = Files.copy(input, temporary, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception exception) {
            Files.deleteIfExists(temporary);
            throw exception;
        }

        String hash = HexFormat.of().formatHex(digest.digest());
        String key = "objects/%s/%s".formatted(hash.substring(0, 2), hash);
        Path target = safeResolve(key);
        Files.createDirectories(target.getParent());
        boolean deduplicated = Files.exists(target);
        if (deduplicated) {
            Files.deleteIfExists(temporary);
        } else {
            moveAtomically(temporary, target);
        }
        return new StoredObject(key, hash, copied, deduplicated);
    }

    @Override
    public InputStream open(String key) throws IOException {
        Path path = safeResolve(key);
        if (!Files.isRegularFile(path)) {
            throw new IllegalArgumentException("存储对象不存在或已被清理：" + key);
        }
        return Files.newInputStream(path);
    }

    private Path safeResolve(String key) {
        Path path = root.resolve(key).normalize();
        if (!path.startsWith(root)) {
            throw new IllegalArgumentException("存储对象键不合法");
        }
        return path;
    }

    private MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前 JRE 不支持 SHA-256", exception);
        }
    }

    private void moveAtomically(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, target);
        }
    }
}
