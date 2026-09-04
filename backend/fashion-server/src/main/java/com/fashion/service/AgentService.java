package com.fashion.service;

import com.fashion.dto.AgentChatResponse;
import com.fashion.dto.AgentInternalChatRequest;

public interface AgentService {
    AgentChatResponse chat(AgentInternalChatRequest request);
}
