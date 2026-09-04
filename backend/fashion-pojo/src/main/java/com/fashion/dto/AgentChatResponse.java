package com.fashion.dto;

import java.util.List;
import java.util.Map;
import java.util.ArrayList;

public class AgentChatResponse {

    private String reply;
    private String sessionId;
    private List<Map<String, Object>> products = new ArrayList<>();
    private Boolean degraded = false;
    private List<String> degradationReasons = new ArrayList<>();

    public String getReply() {
        return reply;
    }

    public void setReply(String reply) {
        this.reply = reply;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public List<Map<String, Object>> getProducts() {
        return products;
    }

    public void setProducts(List<Map<String, Object>> products) {
        this.products = products;
    }

    public Boolean getDegraded() { return degraded; }
    public void setDegraded(Boolean degraded) { this.degraded = degraded; }
    public List<String> getDegradationReasons() { return degradationReasons; }
    public void setDegradationReasons(List<String> degradationReasons) {
        this.degradationReasons = degradationReasons;
    }
}
