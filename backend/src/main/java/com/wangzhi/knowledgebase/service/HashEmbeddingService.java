package com.wangzhi.knowledgebase.service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class HashEmbeddingService implements EmbeddingService {

    private static final int DIMENSIONS = 256;

    @Override
    public double[] embed(String text) {
        double[] vector = new double[DIMENSIONS];
        for (String token : tokenize(text)) {
            int hash = token.hashCode();
            int index = Math.floorMod(hash, DIMENSIONS);
            vector[index] += 1.0;
        }
        normalize(vector);
        return vector;
    }

    private List<String> tokenize(String text) {
        String normalized = text == null ? "" : text.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        List<String> tokens = new ArrayList<>();
        for (String word : normalized.split("[^\\p{L}\\p{N}]+")) {
            if (!word.isBlank()) {
                tokens.add(word);
            }
        }
        int[] codePoints = normalized.codePoints()
                .filter(cp -> Character.isLetterOrDigit(cp))
                .toArray();
        for (int codePoint : codePoints) {
            tokens.add(new String(Character.toChars(codePoint)));
        }
        for (int i = 0; i < codePoints.length - 1; i++) {
            tokens.add(new String(codePoints, i, 2));
        }
        return tokens;
    }

    private void normalize(double[] vector) {
        double norm = 0;
        for (double value : vector) {
            norm += value * value;
        }
        norm = Math.sqrt(norm);
        if (norm == 0) {
            return;
        }
        for (int i = 0; i < vector.length; i++) {
            vector[i] /= norm;
        }
    }
}
