package com.wangzhi.knowledgebase.security;

import java.io.InputStream;

public interface FileSecurityScanner {

    void scan(InputStream input, String originalName) throws Exception;

    String provider();

    default boolean active() {
        return true;
    }

    default boolean available() {
        return active();
    }
}
