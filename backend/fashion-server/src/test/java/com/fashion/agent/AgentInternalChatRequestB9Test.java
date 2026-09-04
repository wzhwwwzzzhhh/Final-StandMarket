package com.fashion.agent;

import com.fashion.dto.AgentInternalChatRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentInternalChatRequestB9Test {

    @Test
    void keepsAuthenticatedUserIdAsLong() {
        AgentInternalChatRequest request = new AgentInternalChatRequest();
        long userId = (long) Integer.MAX_VALUE + 42L;

        request.setUserId(userId);
        request.setMessage("hello");
        request.setSessionId("abcdefghijklmnopqrstuv");
        request.setUserAuthorization("Bearer delegated-user-token");

        assertEquals(Long.valueOf(userId), request.getUserId());
        assertEquals("Bearer delegated-user-token", request.getUserAuthorization());
    }
}
