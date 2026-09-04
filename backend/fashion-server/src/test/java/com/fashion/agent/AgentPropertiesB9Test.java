package com.fashion.agent;

import com.fashion.config.AgentProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AgentPropertiesB9Test {

    @Test
    void normalizesTrailingSlashesExactlyOnce() {
        AgentProperties properties = validProperties("http://127.0.0.1:8000///");

        assertEquals("http://127.0.0.1:8000/chat", properties.validateAndGetChatUrl(new String[]{"dev"}));
    }

    @Test
    void rejectsNonLoopbackPlainHttpOutsideExplicitTestProfile() {
        AgentProperties properties = validProperties("http://agent.internal:8000");
        properties.setAllowInsecureHttp(true);

        assertThrows(IllegalArgumentException.class,
                () -> properties.validateAndGetChatUrl(new String[]{"dev"}));
    }

    @Test
    void permitsExplicitInsecureHttpOnlyInTestProfile() {
        AgentProperties properties = validProperties("http://agent.test:8000");
        properties.setAllowInsecureHttp(true);

        assertEquals("http://agent.test:8000/chat",
                properties.validateAndGetChatUrl(new String[]{"test"}));
    }

    @Test
    void rejectsWeakCredentialAndInvalidTimeouts() {
        AgentProperties weak = validProperties("https://agent.example.com");
        weak.setInternalToken("too-short");
        assertThrows(IllegalArgumentException.class,
                () -> weak.validateAndGetChatUrl(new String[]{"prod"}));

        AgentProperties invalidTimeout = validProperties("https://agent.example.com");
        invalidTimeout.setReadTimeoutMs(0);
        assertThrows(IllegalArgumentException.class,
                () -> invalidTimeout.validateAndGetChatUrl(new String[]{"prod"}));
    }

    @Test
    void rejectsLoopbackLookalikeAndCredentialsContainingWhitespace() {
        AgentProperties lookalike = validProperties("http://127.attacker.example:8000");
        assertThrows(IllegalArgumentException.class,
                () -> lookalike.validateAndGetChatUrl(new String[]{"dev"}));

        AgentProperties whitespaceCredential = validProperties("https://agent.example.com");
        whitespaceCredential.setInternalToken("0123456789abcdef 123456789abcdef0");
        assertThrows(IllegalArgumentException.class,
                () -> whitespaceCredential.validateAndGetChatUrl(new String[]{"prod"}));
    }

    private AgentProperties validProperties(String baseUrl) {
        AgentProperties properties = new AgentProperties();
        properties.setBaseUrl(baseUrl);
        properties.setConnectTimeoutMs(3000);
        properties.setReadTimeoutMs(10000);
        properties.setInternalToken("0123456789abcdef0123456789abcdef");
        return properties;
    }
}
