package com.wangzhi.knowledgebase;

import com.wangzhi.knowledgebase.domain.ImportBatchStatus;
import com.wangzhi.knowledgebase.domain.ImportFileStatus;
import com.wangzhi.knowledgebase.domain.KnowledgeStatus;
import com.wangzhi.knowledgebase.dto.ImportDtos.BatchView;
import com.wangzhi.knowledgebase.service.ImportBatchService;
import com.wangzhi.knowledgebase.service.KnowledgeService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "app.demo-data=false",
        "app.import.storage-path=${java.io.tmpdir}/myrag-import-tests",
        "spring.datasource.url=jdbc:h2:mem:import_pipeline;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE"
})
class ImportPipelineIntegrationTest {

    @Autowired
    private ImportBatchService importBatchService;

    @Autowired
    private KnowledgeService knowledgeService;

    @TempDir
    Path tempDir;

    @Test
    void shouldProcessBatchAndSubmitDocumentsForReview() throws Exception {
        MockMultipartFile first = new MockMultipartFile("files", "会员积分规则.txt", "text/plain",
                "会员积分可以在结算时抵扣，每一百积分抵扣一元，每单最多抵扣商品金额的百分之十。".getBytes(StandardCharsets.UTF_8));
        MockMultipartFile second = new MockMultipartFile("files", "配送时效.md", "text/markdown",
                "# 配送时效\n现货订单通常在付款后二十四小时内出库，核心城市预计一至两天送达。".getBytes(StandardCharsets.UTF_8));

        BatchView created = importBatchService.create(new MockMultipartFile[]{first, second}, "订单服务", "测试用户", "自动导入");
        BatchView completed = awaitCompletion(created.id());

        assertThat(completed.status()).isEqualTo(ImportBatchStatus.READY);
        assertThat(completed.succeededFiles()).isEqualTo(2);
        assertThat(completed.files()).allMatch(file -> file.status() == ImportFileStatus.READY);
        assertThat(completed.files()).allMatch(file -> file.documentId() != null);

        BatchView submitted = importBatchService.submit(created.id());
        assertThat(submitted.status()).isEqualTo(ImportBatchStatus.SUBMITTED);
        submitted.files().forEach(file -> assertThat(knowledgeService.get(file.documentId()).status())
                .isEqualTo(KnowledgeStatus.PENDING_REVIEW));
        assertThat(new String(importBatchService.report(created.id()), StandardCharsets.UTF_8)).contains("会员积分规则.txt");
    }

    private BatchView awaitCompletion(String batchId) throws InterruptedException {
        for (int attempt = 0; attempt < 60; attempt++) {
            BatchView batch = importBatchService.get(batchId);
            if (batch.status() != ImportBatchStatus.QUEUED && batch.status() != ImportBatchStatus.PROCESSING) {
                return batch;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("导入批次处理超时");
    }
}
