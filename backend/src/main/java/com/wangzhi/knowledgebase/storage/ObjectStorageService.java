package com.wangzhi.knowledgebase.storage;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;

public interface ObjectStorageService {

    StoredObject store(MultipartFile file) throws IOException;

    InputStream open(String key) throws IOException;
}
