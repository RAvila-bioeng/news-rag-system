package com.ragnews.ingestion.config;

import java.util.Map;

public class SourceConfig {

    private String name;
    private String url;
    private Map<String, String> params;
    private String schedule;

    public String getName() {
        return name;
    }

    public String getUrl() {
        return url;
    }

    public Map<String, String> getParams() {
        return params;
    }

    public String getSchedule() {
        return schedule;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public void setParams(Map<String, String> params) {
        this.params = params;
    }

    public void setSchedule(String schedule) {
        this.schedule = schedule;
    }
}