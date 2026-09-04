package com.fashion.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fashion.context.BaseContext;
import com.fashion.controller.user.AgentController;
import com.fashion.dto.AgentChatResponse;
import com.fashion.dto.AgentInternalChatRequest;
import com.fashion.service.AgentService;
import com.fashion.service.OrderService;
import com.fashion.util.AgentSessionIdGenerator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AgentControllerB9ContractTest {

    private AgentService agentService;
    private AgentSessionIdGenerator sessionIdGenerator;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        agentService = mock(AgentService.class);
        sessionIdGenerator = mock(AgentSessionIdGenerator.class);
        AgentController controller = new AgentController(agentService, mock(OrderService.class), sessionIdGenerator);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @AfterEach
    void cleanContext() {
        BaseContext.removeUserId();
    }

    @Test
    void ignoresBrowserIdentityAndBuildsAuthenticatedLongInternalRequest() throws Exception {
        long userId = (long) Integer.MAX_VALUE + 42L;
        BaseContext.setUserId(userId);
        when(sessionIdGenerator.generate()).thenReturn("abcdefghijklmnopqrstuv");
        when(agentService.chat(any(AgentInternalChatRequest.class)))
                .thenReturn(healthyResponse("abcdefghijklmnopqrstuv"));

        mockMvc.perform(post("/user/agent/chat")
                        .header("Authorization", "Bearer delegated-user-token")
                        .accept(MediaType.APPLICATION_JSON)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"hello\",\"userId\":1,\"token\":\"attacker\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.data.sessionId").value("abcdefghijklmnopqrstuv"));

        ArgumentCaptor<AgentInternalChatRequest> captor = ArgumentCaptor.forClass(AgentInternalChatRequest.class);
        verify(agentService).chat(captor.capture());
        assertEquals(Long.valueOf(userId), captor.getValue().getUserId());
        assertEquals("Bearer delegated-user-token", captor.getValue().getUserAuthorization());
        assertEquals("hello", captor.getValue().getMessage());
    }

    @Test
    void rejectsInvalidMessageBeforeCallingPython() throws Exception {
        BaseContext.setUserId(10L);

        mockMvc.perform(post("/user/agent/chat")
                        .header("Authorization", "Bearer delegated-user-token")
                        .accept(MediaType.APPLICATION_JSON)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"   \"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.msg").value("INVALID_MESSAGE"));

        verify(agentService, never()).chat(any());
    }

    @Test
    void rejectsLegacySessionBeforeCallingPython() throws Exception {
        BaseContext.setUserId(10L);
        when(sessionIdGenerator.isValid("0123456789abcdef")).thenReturn(false);

        mockMvc.perform(post("/user/agent/chat")
                        .header("Authorization", "Bearer delegated-user-token")
                        .accept(MediaType.APPLICATION_JSON)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"hello\",\"sessionId\":\"0123456789abcdef\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.msg").value("INVALID_SESSION_ID"));

        verify(agentService, never()).chat(any());
    }

    @Test
    void rejectsBlankDelegatedBearerBeforeCallingPython() throws Exception {
        BaseContext.setUserId(10L);

        mockMvc.perform(post("/user/agent/chat")
                        .header("Authorization", "Bearer ")
                        .accept(MediaType.APPLICATION_JSON)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"hello\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.msg").value("UNAUTHORIZED"));

        verify(agentService, never()).chat(any());
    }

    @Test
    void rejectsNonStringMessageAndSessionTypesBeforeCallingPython() throws Exception {
        BaseContext.setUserId(10L);

        mockMvc.perform(post("/user/agent/chat")
                        .header("Authorization", "Bearer delegated-user-token")
                        .accept(MediaType.APPLICATION_JSON)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":123}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.msg").value("INVALID_MESSAGE"));

        mockMvc.perform(post("/user/agent/chat")
                        .header("Authorization", "Bearer delegated-user-token")
                        .accept(MediaType.APPLICATION_JSON)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"hello\",\"sessionId\":123}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.msg").value("INVALID_SESSION_ID"));

        verify(agentService, never()).chat(any());
    }

    @Test
    void rejectsExplicitNullOrBlankSessionButAllowsOmission() throws Exception {
        BaseContext.setUserId(10L);

        for (String sessionJson : new String[]{"null", "\"   \""}) {
            mockMvc.perform(post("/user/agent/chat")
                            .header("Authorization", "Bearer delegated-user-token")
                            .accept(MediaType.APPLICATION_JSON)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"message\":\"hello\",\"sessionId\":" + sessionJson + "}"))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.msg").value("INVALID_SESSION_ID"));
        }

        verify(agentService, never()).chat(any());
    }

    private AgentChatResponse healthyResponse(String sessionId) {
        AgentChatResponse response = new AgentChatResponse();
        response.setReply("ok");
        response.setSessionId(sessionId);
        response.setProducts(new ArrayList<>());
        response.setDegraded(false);
        response.setDegradationReasons(new ArrayList<>());
        return response;
    }
}
