package com.ragnews.ingestion.embedding;

import com.ragnews.ingestion.parser.NormalizedArticle;

import java.util.List;

public interface EmbeddingGenerator {

    List<Double> generate(NormalizedArticle article);
}
