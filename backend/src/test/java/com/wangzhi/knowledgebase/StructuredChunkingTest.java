package com.wangzhi.knowledgebase;

import com.wangzhi.knowledgebase.service.ChunkingService;
import com.wangzhi.knowledgebase.service.ParsedBlock;
import com.wangzhi.knowledgebase.service.TokenCounter;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StructuredChunkingTest {

    @Test
    void shouldKeepHeadingLocatorAndStableContentHash() {
        ChunkingService service = new ChunkingService(new TokenCounter(), 100, 20);
        List<ParsedBlock> blocks = List.of(
                new ParsedBlock("heading", "退款规则", 2, "售后政策 > 退款规则", "page:2#block:1"),
                new ParsedBlock("paragraph", "消费者提交申请后，系统会检查订单状态和商品资格。".repeat(5),
                        2, "售后政策 > 退款规则", "page:2#block:2"),
                new ParsedBlock("table-row", "状态 | 处理方式\n已发货 | 联系客服", 3,
                        "售后政策 > 退款规则", "page:3#block:3"));

        var first = service.split("帮助中心", blocks);
        var second = service.split("帮助中心", blocks);

        assertThat(first).hasSizeGreaterThan(1);
        assertThat(first).allMatch(chunk -> chunk.tokenCount() > 0 && chunk.tokenCount() <= 120);
        assertThat(first).allMatch(chunk -> chunk.headingPath().contains("退款规则"));
        assertThat(first.getFirst().locator()).startsWith("page:2");
        assertThat(first.stream().map(ChunkingService.ChunkDraft::contentHash).toList())
                .isEqualTo(second.stream().map(ChunkingService.ChunkDraft::contentHash).toList());
    }
}
