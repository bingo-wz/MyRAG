package com.wangzhi.knowledgebase;

import com.wangzhi.knowledgebase.storage.ContentAddressedFileStorageService;
import com.wangzhi.knowledgebase.storage.StoredObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ContentAddressedStorageTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldDeduplicateSameContentAndReadItBack() throws Exception {
        ContentAddressedFileStorageService storage = new ContentAddressedFileStorageService(tempDir.toString());
        byte[] content = "相同文件内容只应保存一份".getBytes(StandardCharsets.UTF_8);

        StoredObject first = storage.store(new MockMultipartFile("files", "first.txt", "text/plain", content));
        StoredObject second = storage.store(new MockMultipartFile("files", "renamed.txt", "text/plain", content));

        assertThat(first.key()).isEqualTo(second.key());
        assertThat(first.sha256()).hasSize(64);
        assertThat(first.deduplicated()).isFalse();
        assertThat(second.deduplicated()).isTrue();
        try (var input = storage.open(first.key())) {
            assertThat(input.readAllBytes()).isEqualTo(content);
        }
    }
}
