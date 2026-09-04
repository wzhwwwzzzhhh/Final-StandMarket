package com.fashion.dto;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import java.io.IOException;

public class AgentChatRequest {

    @JsonDeserialize(using = StrictStringDeserializer.class)
    private String sessionId;

    @JsonDeserialize(using = StrictStringDeserializer.class)
    private String message;

    public String getSessionId() {
        return "\u0000".equals(sessionId) ? null : sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId == null ? "\u0000" : sessionId;
    }

    public boolean sessionIdWasProvided() {
        return sessionId != null;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public static class StrictStringDeserializer extends JsonDeserializer<String> {

        @Override
        public String deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            if (!parser.hasToken(JsonToken.VALUE_STRING)) {
                return (String) context.handleUnexpectedToken(String.class, parser);
            }
            return parser.getText();
        }
    }

}
