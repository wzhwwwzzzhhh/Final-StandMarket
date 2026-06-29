package com.fashion.service;

import com.fashion.dto.AgentChatRequest;
import com.fashion.dto.AgentChatResponse;

public interface AgentService {
    AgentChatResponse chat(AgentChatRequest request);
}
