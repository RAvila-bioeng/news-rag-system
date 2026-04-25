package com.ragnews.ingestion.model;

import io.micronaut.serde.annotation.Serdeable;

import java.util.List;

@Serdeable
public class NewsApiResponse {

    private String status;
    private int totalResults;
    private List<Article> articles;

    public String getStatus() {
        return status;
    }

    public int getTotalResults() {
        return totalResults;
    }

    public List<Article> getArticles() {
        return articles;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public void setTotalResults(int totalResults) {
        this.totalResults = totalResults;
    }

    public void setArticles(List<Article> articles) {
        this.articles = articles;
    }

    @Serdeable
    public static class Article {

        private Source source;
        private String author;
        private String title;
        private String description;
        private String url;
        private String urlToImage;
        private String publishedAt;
        private String content;

        public Source getSource() {
            return source;
        }

        public String getAuthor() {
            return author;
        }

        public String getTitle() {
            return title;
        }

        public String getDescription() {
            return description;
        }

        public String getUrl() {
            return url;
        }

        public String getUrlToImage() {
            return urlToImage;
        }

        public String getPublishedAt() {
            return publishedAt;
        }

        public String getContent() {
            return content;
        }

        public void setSource(Source source) {
            this.source = source;
        }

        public void setAuthor(String author) {
            this.author = author;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public void setUrlToImage(String urlToImage) {
            this.urlToImage = urlToImage;
        }

        public void setPublishedAt(String publishedAt) {
            this.publishedAt = publishedAt;
        }

        public void setContent(String content) {
            this.content = content;
        }
    }

    @Serdeable
    public static class Source {

        private String id;
        private String name;

        public String getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public void setId(String id) {
            this.id = id;
        }

        public void setName(String name) {
            this.name = name;
        }
    }
}