package com.wangzhi.knowledgebase.service;

import com.wangzhi.knowledgebase.service.TextExtractionService.ExtractionResult;
import com.wangzhi.knowledgebase.storage.ObjectStorageService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Service
public class TimedTextExtractionService {

    private final TextExtractionService extractionService;
    private final ObjectStorageService storageService;
    private final ExecutorService fileParsingExecutor;
    private final long timeoutSeconds;

    public TimedTextExtractionService(TextExtractionService extractionService,
                                      ObjectStorageService storageService,
                                      ExecutorService fileParsingExecutor,
                                      @Value("${app.import.parse-timeout-seconds:120}") long timeoutSeconds) {
        this.extractionService = extractionService;
        this.storageService = storageService;
        this.fileParsingExecutor = fileParsingExecutor;
        this.timeoutSeconds = Math.max(1, timeoutSeconds);
    }

    public ExtractionResult extract(String storageKey, String originalName) throws Exception {
        Future<ExtractionResult> future = fileParsingExecutor.submit(() -> {
            try (InputStream input = storageService.open(storageKey)) {
                return extractionService.extract(input, originalName);
            }
        });
        try {
            return future.get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (TimeoutException exception) {
            future.cancel(true);
            throw new IllegalStateException("文件解析超过最大处理时间：" + timeoutSeconds + " 秒", exception);
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof Exception checked) {
                throw checked;
            }
            throw new IllegalStateException("文件解析线程异常", cause);
        }
    }
}
