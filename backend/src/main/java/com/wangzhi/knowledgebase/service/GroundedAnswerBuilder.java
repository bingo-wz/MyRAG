package com.wangzhi.knowledgebase.service;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class GroundedAnswerBuilder {

    private static final Pattern CITATION = Pattern.compile("\\[S(\\d+)]");
    private static final String REFUSAL = "当前已生效知识中没有找到足够可靠的依据。建议换一种问法，或联系知识管理员补充相关资料。";

    public String extractive(List<RetrievedChunk> retrieved) {
        if (retrieved.isEmpty()) {
            return REFUSAL;
        }
        StringBuilder answer = new StringBuilder("根据知识库中已审核生效的内容：\n\n");
        int count = Math.min(3, retrieved.size());
        for (int index = 0; index < count; index++) {
            String text = retrieved.get(index).content().replaceAll("\\s+", " ").trim();
            if (text.length() > 220) {
                text = text.substring(0, 220) + "…";
            }
            answer.append(index + 1).append(". ").append(text)
                    .append(" [S").append(index + 1).append("]\n");
        }
        return answer.append("\n以上结论均可通过对应来源核对。").toString();
    }

    public boolean hasOnlyValidCitations(String answer, int sourceCount) {
        if (answer == null || answer.isBlank() || sourceCount == 0) {
            return false;
        }
        Matcher matcher = CITATION.matcher(answer);
        boolean found = false;
        while (matcher.find()) {
            found = true;
            int index = Integer.parseInt(matcher.group(1));
            if (index < 1 || index > sourceCount) {
                return false;
            }
        }
        return found;
    }

    public String refusal() {
        return REFUSAL;
    }
}
