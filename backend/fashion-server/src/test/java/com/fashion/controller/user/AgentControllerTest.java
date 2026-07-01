package com.fashion.controller.user;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fashion.dto.AgentChatRequest;
import com.fashion.dto.AgentChatResponse;
import com.fashion.interceptor.BaseContext;
import com.fashion.result.Result;
import com.fashion.service.AgentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.bean.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.lang.reflect.Method;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AgentController.class)
@DisplayName("AgentController 单元测试")
class AgentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AgentService agentService;

    private AgentChatRequest request;
    private AgentChatResponse response;

    @BeforeEach
    void setUp() {
        request = new AgentChatRequest();
        request.setMessage("帮我找一件连衣裙");

        response = new AgentChatResponse();
        response.setReply("为您推荐以下连衣裙...");
        response.setSessionId("test-session");
    }

    @Test
    @DisplayName("测试用户已登录，成功调用 Agent")
    void testChatSuccess() throws Exception {
        when(agentService.chat(any(AgentChatRequest.class))).thenReturn(response);

        try {
            Method method = BaseContext.class.getMethod("setCurrentId", Long.class);
            method.invoke(null, 1L);
        } catch (Exception e) {
        }

        mockMvc.perform(post("/user/agent/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.data.reply").value("为您推荐以下连衣裙..."))
                .andExpect(jsonPath("$.data.sessionId").value("test-session"));

        verify(agentService, times(1)).chat(any(AgentChatRequest.class));
    }

    @Test
    @DisplayName("测试用户未登录，返回错误提示")
    void testChatNotLoggedIn() throws Exception {
        try {
            Method method = BaseContext.class.getMethod("setCurrentId", Long.class);
            method.invoke(null, (Long) null);
        } catch (Exception e) {
        }

        mockMvc.perform(post("/user/agent/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.msg").value("请先登录"));

        verify(agentService, never()).chat(any(AgentChatRequest.class));
    }

    @Test
    @DisplayName("测试请求体为空，返回 400 错误")
    void testChatEmptyRequest() throws Exception {
        mockMvc.perform(post("/user/agent/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(""))
                .andExpect(status().isBadRequest());
    }
}
