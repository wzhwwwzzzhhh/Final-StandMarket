package com.fashion.agent;

import com.fashion.dto.AgentChatRequest;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentChatRequestB9ContractTest {

    @Test
    void browserRequestContainsOnlyBusinessInput() {
        Set<String> fields = Arrays.stream(AgentChatRequest.class.getDeclaredFields())
                .map(Field::getName)
                .collect(Collectors.toSet());

        assertEquals(new HashSet<>(Arrays.asList("message", "sessionId")), fields);
    }
}
