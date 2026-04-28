package com.ragnews.search.embedding;

import java.util.List;

public interface EmbeddingGenerator {

    List<Double> generateEmbedding(String text);
}
