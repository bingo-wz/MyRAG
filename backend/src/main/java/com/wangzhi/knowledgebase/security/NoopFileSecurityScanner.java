package com.wangzhi.knowledgebase.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.InputStream;

@Service
@ConditionalOnProperty(name = "app.security.file-scan.provider", havingValue = "noop", matchIfMissing = true)
public class NoopFileSecurityScanner implements FileSecurityScanner {

    @Override
    public void scan(InputStream input, String originalName) {
    }

    @Override
    public String provider() {
        return "noop";
    }

    @Override
    public boolean active() {
        return false;
    }
}
