package com.ragnews.ingestion.enrichment;

import com.ragnews.ingestion.model.ProcessedArticle;
import com.ragnews.ingestion.parser.NormalizedArticle;
import com.ragnews.ingestion.sentiment.Sentiment;
import com.ragnews.ingestion.sentiment.SentimentAnalyzer;
import jakarta.inject.Singleton;

import java.util.List;

@Singleton
public class ArticleEnricher {

    private final SentimentAnalyzer sentimentAnalyzer;

    public ArticleEnricher(SentimentAnalyzer sentimentAnalyzer) {
        this.sentimentAnalyzer = sentimentAnalyzer;
    }

    public List<ProcessedArticle> enrich(List<NormalizedArticle> articles) {
        return articles.stream()
                .map(this::enrich)
                .toList();
    }

    private ProcessedArticle enrich(NormalizedArticle article) {
        Sentiment sentiment = sentimentAnalyzer.analyze(article);
        return new ProcessedArticle(article, sentiment);
    }
}
