package com.ragnews.ingestion.parser;

import java.util.List;

public interface ArticleParser<T> {

    List<NormalizedArticle> parse(T rawResponse);
}
