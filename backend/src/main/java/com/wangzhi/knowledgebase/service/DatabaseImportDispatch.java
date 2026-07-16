package com.wangzhi.knowledgebase.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "false", matchIfMissing = true)
public class DatabaseImportDispatch implements ImportDispatch {
    @Override
    public void dispatch(String batchId) {
        // 数据库恢复 Worker 会自动领取任务，开发模式无需额外消息。
    }
}
