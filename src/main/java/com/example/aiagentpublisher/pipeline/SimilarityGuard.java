package com.example.aiagentpublisher.pipeline;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
public class SimilarityGuard {

    private static final int SHINGLE_SIZE = 8;

    public boolean isTooSimilar(String generatedText, List<String> exampleTexts) {
        Set<String> generatedShingles = shingles(generatedText);
        if (generatedShingles.isEmpty()) {
            return false;
        }
        for (String example : exampleTexts) {
            Set<String> overlap = shingles(example);
            overlap.retainAll(generatedShingles);
            if (!overlap.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private Set<String> shingles(String text) {
        Set<String> result = new HashSet<>();
        if (StringUtils.isBlank(text)) {
            return result;
        }
        List<String> words = List.of(StringUtils.split(StringUtils.lowerCase(text)));
        for (int i = 0; i + SHINGLE_SIZE <= words.size(); i++) {
            result.add(String.join(" ", words.subList(i, i + SHINGLE_SIZE)));
        }
        return result;
    }
}
