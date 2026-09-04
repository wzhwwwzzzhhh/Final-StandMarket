package com.fashion.dto;

public class AgentInternalChatRequest {

    private Long userId;
    private String sessionId;
    private String message;
    private String userAuthorization;

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getUserAuthorization() { return userAuthorization; }
    public void setUserAuthorization(String userAuthorization) { this.userAuthorization = userAuthorization; }
}
