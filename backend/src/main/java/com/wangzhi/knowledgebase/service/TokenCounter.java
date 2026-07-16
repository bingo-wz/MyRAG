package com.wangzhi.knowledgebase.service;

import org.springframework.stereotype.Component;

@Component
public class TokenCounter {

    public int count(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        int tokens = 0;
        int latinRun = 0;
        for (int offset = 0; offset < text.length();) {
            int codePoint = text.codePointAt(offset);
            offset += Character.charCount(codePoint);
            if (isCjk(codePoint) || (!Character.isLetterOrDigit(codePoint) && !Character.isWhitespace(codePoint))) {
                tokens += flushLatin(latinRun) + 1;
                latinRun = 0;
            } else if (Character.isLetterOrDigit(codePoint)) {
                latinRun++;
            } else {
                tokens += flushLatin(latinRun);
                latinRun = 0;
            }
        }
        return tokens + flushLatin(latinRun);
    }

    public int endOffsetForTokens(String text, int startOffset, int tokenBudget) {
        if (tokenBudget <= 0 || startOffset >= text.length()) {
            return startOffset;
        }
        int low = Math.min(text.length(), startOffset + 1);
        int high = text.length();
        int best = low;
        while (low <= high) {
            int middle = (low + high) >>> 1;
            if (middle < text.length() && Character.isLowSurrogate(text.charAt(middle))) {
                middle--;
            }
            int count = count(text.substring(startOffset, middle));
            if (count <= tokenBudget) {
                best = middle;
                low = middle + 1;
            } else {
                high = middle - 1;
            }
        }
        return Math.max(best, startOffset + 1);
    }

    private int flushLatin(int length) {
        return length == 0 ? 0 : Math.max(1, (length + 3) / 4);
    }

    private boolean isCjk(int codePoint) {
        Character.UnicodeScript script = Character.UnicodeScript.of(codePoint);
        return script == Character.UnicodeScript.HAN
                || script == Character.UnicodeScript.HIRAGANA
                || script == Character.UnicodeScript.KATAKANA
                || script == Character.UnicodeScript.HANGUL;
    }
}
