package com.ragnews.ingestion.sentiment;

import com.ragnews.ingestion.parser.NormalizedArticle;

public interface SentimentAnalyzer {

    Sentiment analyze(NormalizedArticle article);
}
