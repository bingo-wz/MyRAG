package com.wangzhi.knowledgebase;

import com.wangzhi.knowledgebase.domain.KnowledgeStatus;
import com.wangzhi.knowledgebase.dto.KnowledgeDtos.CreateRequest;
import com.wangzhi.knowledgebase.dto.KnowledgeDtos.ReviewRequest;
import com.wangzhi.knowledgebase.dto.KnowledgeDtos.View;
import com.wangzhi.knowledgebase.dto.QaDtos.AskRequest;
import com.wangzhi.knowledgebase.dto.QaDtos.AskResponse;
import com.wangzhi.knowledgebase.dto.QaDtos.FeedbackRequest;
import com.wangzhi.knowledgebase.repository.QuestionLogRepository;
import com.wangzhi.knowledgebase.service.KnowledgeService;
import com.wangzhi.knowledgebase.service.QaService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = "app.demo-data=false")
class KnowledgeWorkflowIntegrationTest {

    @Autowired
    private KnowledgeService knowledgeService;

    @Autowired
    private QaService qaService;

    @Autowired
    private QuestionLogRepository logRepository;

    @Test
    void shouldCompleteReviewRetrievalAndFeedbackWorkflow() {
        View draft = knowledgeService.create(new CreateRequest(
                "退换货规则", "用户签收商品七日内，在商品完好且配件齐全的情况下可以申请无理由退货。",
                "售后服务", "服务政策中心", "退货,售后", "测试用户"));

        assertThat(draft.status()).isEqualTo(KnowledgeStatus.DRAFT);
        assertThat(draft.chunkCount()).isPositive();

        View pending = knowledgeService.submit(draft.id());
        assertThat(pending.status()).isEqualTo(KnowledgeStatus.PENDING_REVIEW);

        View approved = knowledgeService.review(draft.id(), new ReviewRequest(true, "审核员", "规则准确"));
        assertThat(approved.status()).isEqualTo(KnowledgeStatus.APPROVED);

        AskResponse answer = qaService.ask(new AskRequest("签收六天还能退货吗", "售后服务"));
        assertThat(answer.sources()).isNotEmpty();
        assertThat(answer.sources().getFirst().documentId()).isEqualTo(draft.id());
        assertThat(answer.answer()).contains("七日");

        qaService.feedback(answer.traceId(), new FeedbackRequest(false, "缺少例外条件"));
        assertThat(logRepository.findByTraceId(answer.traceId())).get()
                .satisfies(log -> {
                    assertThat(log.isBadCase()).isTrue();
                    assertThat(log.getBadReason()).isEqualTo("缺少例外条件");
                });

        assertThatThrownBy(() -> knowledgeService.update(draft.id(), new com.wangzhi.knowledgebase.dto.KnowledgeDtos.UpdateRequest(
                "修改标题", approved.content(), approved.domain(), approved.source(), "测试")))
                .hasMessageContaining("不能直接编辑");
    }
}
