package com.ragnews.ingestion.storage;

import com.ragnews.ingestion.model.ProcessedArticle;

import java.util.List;

public interface ArticleIndexer {

    int indexArticles(List<ProcessedArticle> articles);
}
