package com.fashion.dto;

import java.util.List;
import java.util.Map;

public class AgentChatResponse {

    private String reply;
    private String sessionId;
    private List<Map<String, Object>> products;

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
}
