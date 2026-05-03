package com.ragnews.ingestion.config;

public class ResponseMappingConfig {

    private String articlesPath;
    private String title;
    private String content;
    private String url;
    private String source;
    private String timestamp;

    public String getArticlesPath() {
        return articlesPath;
    }

    public String getTitle() {
        return title;
    }

    public String getContent() {
        return content;
    }

    public String getUrl() {
        return url;
    }

    public String getSource() {
        return source;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setArticlesPath(String articlesPath) {
        this.articlesPath = articlesPath;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }
}
