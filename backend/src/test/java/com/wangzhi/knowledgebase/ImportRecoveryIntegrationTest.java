package com.wangzhi.knowledgebase;

import com.wangzhi.knowledgebase.domain.ImportBatch;
import com.wangzhi.knowledgebase.domain.ImportBatchStatus;
import com.wangzhi.knowledgebase.domain.ImportFileStatus;
import com.wangzhi.knowledgebase.domain.ImportFileTask;
import com.wangzhi.knowledgebase.dto.ImportDtos.BatchView;
import com.wangzhi.knowledgebase.repository.ImportBatchRepository;
import com.wangzhi.knowledgebase.repository.ImportFileTaskRepository;
import com.wangzhi.knowledgebase.repository.KnowledgeDocumentRepository;
import com.wangzhi.knowledgebase.service.ImportBatchLeaseService;
import com.wangzhi.knowledgebase.service.ImportBatchService;
import com.wangzhi.knowledgebase.service.ImportProcessor;
import com.wangzhi.knowledgebase.service.KnowledgeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.annotation.DirtiesContext;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "app.demo-data=false",
        "app.import.worker-enabled=false",
        "app.import.max-attempts=2",
        "app.import.lease-seconds=30",
        "app.import.storage-path=${java.io.tmpdir}/myrag-recovery-tests",
        "app.storage.root=${java.io.tmpdir}/myrag-recovery-tests",
        "spring.datasource.url=jdbc:h2:mem:import_recovery;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class ImportRecoveryIntegrationTest {

    @Autowired
    private ImportBatchService batchService;

    @Autowired
    private ImportBatchLeaseService leaseService;

    @Autowired
    private ImportProcessor processor;

    @Autowired
    private ImportBatchRepository batchRepository;

    @Autowired
    private ImportFileTaskRepository fileRepository;

    @Autowired
    private KnowledgeDocumentRepository documentRepository;

    @Autowired
    private KnowledgeService knowledgeService;

    @Test
    void shouldRecoverInterruptedIndexingWithoutCreatingDuplicateDocument() {
        BatchView created = createBatch("恢复测试.txt");
        String firstWorker = "worker-first";
        assertThat(leaseService.claim(created.id(), firstWorker)).contains(created.id());
        processor.process(created.id(), firstWorker);

        ImportFileTask completedTask = fileRepository.findByBatchIdOrderByIdAsc(created.id()).getFirst();
        Long originalDocumentId = completedTask.getDocumentId();
        assertThat(originalDocumentId).isNotNull();
        assertThat(documentRepository.count()).isEqualTo(1);

        ImportBatch interruptedBatch = batchRepository.findById(created.id()).orElseThrow();
        interruptedBatch.setStatus(ImportBatchStatus.PROCESSING);
        interruptedBatch.setWorkerId("dead-worker");
        interruptedBatch.setLeaseUntil(LocalDateTime.now().minusSeconds(1));
        interruptedBatch.setAttemptCount(1);
        interruptedBatch.setProcessedFiles(0);
        interruptedBatch.setSucceededFiles(0);
        batchRepository.saveAndFlush(interruptedBatch);

        completedTask.setStatus(ImportFileStatus.INDEXING);
        completedTask.setDocumentId(null);
        fileRepository.saveAndFlush(completedTask);

        String recoveryWorker = "worker-recovery";
        assertThat(leaseService.claim(created.id(), recoveryWorker)).contains(created.id());
        processor.process(created.id(), recoveryWorker);

        ImportFileTask recovered = fileRepository.findById(completedTask.getId()).orElseThrow();
        assertThat(recovered.getStatus()).isEqualTo(ImportFileStatus.READY);
        assertThat(recovered.getDocumentId()).isEqualTo(originalDocumentId);
        assertThat(recovered.getRetryCount()).isEqualTo(1);
        assertThat(documentRepository.count()).isEqualTo(1);
        assertThat(batchRepository.findById(created.id()).orElseThrow().getStatus()).isEqualTo(ImportBatchStatus.READY);

        knowledgeService.delete(originalDocumentId);
        assertThat(documentRepository.count()).isZero();
        assertThat(fileRepository.findById(completedTask.getId()).orElseThrow().getDocumentId()).isNull();
    }

    @Test
    void shouldFailBatchWhenAutomaticRecoveryAttemptsAreExhausted() {
        BatchView created = createBatch("超限测试.txt");
        ImportBatch batch = batchRepository.findById(created.id()).orElseThrow();
        batch.setStatus(ImportBatchStatus.PROCESSING);
        batch.setWorkerId("dead-worker");
        batch.setLeaseUntil(LocalDateTime.now().minusSeconds(1));
        batch.setAttemptCount(2);
        batchRepository.saveAndFlush(batch);

        ImportFileTask task = fileRepository.findByBatchIdOrderByIdAsc(created.id()).getFirst();
        task.setStatus(ImportFileStatus.EXTRACTING);
        fileRepository.saveAndFlush(task);

        assertThat(leaseService.claim(created.id(), "worker-too-late")).isEmpty();
        assertThat(batchRepository.findById(created.id()).orElseThrow().getStatus()).isEqualTo(ImportBatchStatus.FAILED);
        ImportFileTask failed = fileRepository.findById(task.getId()).orElseThrow();
        assertThat(failed.getStatus()).isEqualTo(ImportFileStatus.FAILED);
        assertThat(failed.getErrorMessage()).contains("最大自动恢复次数");
    }

    @Test
    void shouldIgnoreDuplicateDeliveryWhileBatchHasAnActiveLease() {
        BatchView created = createBatch("重复消息测试.txt");
        assertThat(leaseService.claim(created.id(), "worker-owner")).contains(created.id());
        assertThat(leaseService.claim(created.id(), "worker-duplicate")).isEmpty();

        processor.process(created.id(), "worker-owner");

        assertThat(leaseService.claim(created.id(), "worker-after-completion")).isEmpty();
        assertThat(documentRepository.findByImportTaskId(
                fileRepository.findByBatchIdOrderByIdAsc(created.id()).getFirst().getId())).isPresent();
    }

    private BatchView createBatch(String filename) {
        MockMultipartFile file = new MockMultipartFile("files", filename, "text/plain",
                "导入任务发生中断后必须安全恢复，并且不能重复创建知识文档。".getBytes(StandardCharsets.UTF_8));
        return batchService.create(new MockMultipartFile[]{file}, "可靠性测试", "本地用户", "恢复,幂等");
    }
}
