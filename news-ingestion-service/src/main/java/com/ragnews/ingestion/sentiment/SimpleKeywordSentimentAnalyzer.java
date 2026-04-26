package com.ragnews.ingestion.sentiment;

import com.ragnews.ingestion.parser.NormalizedArticle;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Locale;

@Singleton
public class SimpleKeywordSentimentAnalyzer implements SentimentAnalyzer {

    private static final List<String> POSITIVE_KEYWORDS = List.of(
            "growth", "gain", "gains", "record", "surge", "boost",
            "profit", "profits", "positive", "success", "successful",
            "win", "wins", "improve", "improves", "improved",
            "recovery", "strong", "rise", "rises", "rising"
    );

    private static final List<String> NEGATIVE_KEYWORDS = List.of(
            "crash", "drop", "drops", "fall", "falls", "falling",
            "loss", "losses", "negative", "fail", "fails", "failed",
            "war", "crisis", "risk", "risks", "fear", "fears",
            "decline", "declines", "recession", "inflation", "cut", "cuts"
    );

    @Override
    public Sentiment analyze(NormalizedArticle article) {
        String text = buildText(article);

        int positiveScore = countMatches(text, POSITIVE_KEYWORDS);
        int negativeScore = countMatches(text, NEGATIVE_KEYWORDS);

        if (positiveScore > negativeScore) {
            return Sentiment.POSITIVE;
        }

        if (negativeScore > positiveScore) {
            return Sentiment.NEGATIVE;
        }

        return Sentiment.NEUTRAL;
    }

    private String buildText(NormalizedArticle article) {
        String title = article.title() == null ? "" : article.title();
        String content = article.content() == null ? "" : article.content();

        return (title + " " + content).toLowerCase(Locale.ROOT);
    }

    private int countMatches(String text, List<String> keywords) {
        int count = 0;

        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                count++;
            }
        }

        return count;
    }
}
