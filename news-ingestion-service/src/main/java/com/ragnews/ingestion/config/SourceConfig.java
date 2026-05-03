package com.ragnews.ingestion.config;

import java.util.Collections;
import java.util.Map;

public class SourceConfig {

    private String name;
    private boolean enabled = true;
    private String type = "generic-json";
    private String url;
    private String method = "GET";
    private Map<String, String> headers = Collections.emptyMap();
    private Map<String, String> params = Collections.emptyMap();
    private String schedule;
    private ResponseMappingConfig responseMapping;

    public String getName() {
        return name;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getType() {
        return type;
    }

    public String getUrl() {
        return url;
    }

    public String getMethod() {
        return method;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public Map<String, String> getParams() {
        return params;
    }

    public String getSchedule() {
        return schedule;
    }

    public ResponseMappingConfig getResponseMapping() {
        return responseMapping;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void setType(String type) {
        this.type = type;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public void setHeaders(Map<String, String> headers) {
        this.headers = headers;
    }

    public void setParams(Map<String, String> params) {
        this.params = params;
    }

    public void setSchedule(String schedule) {
        this.schedule = schedule;
    }

    public void setResponseMapping(ResponseMappingConfig responseMapping) {
        this.responseMapping = responseMapping;
    }
}
