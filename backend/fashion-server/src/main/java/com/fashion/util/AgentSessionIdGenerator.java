package com.fashion.util;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.regex.Pattern;

@Component
public class AgentSessionIdGenerator {

    private static final Pattern VALID_SESSION = Pattern.compile("[A-Za-z0-9_-]{22,64}");
    private final SecureRandom secureRandom;

    public AgentSessionIdGenerator() {
        this(new SecureRandom());
    }

    AgentSessionIdGenerator(SecureRandom secureRandom) {
        this.secureRandom = secureRandom;
    }

    public String generate() {
        byte[] randomBytes = new byte[16];
        secureRandom.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    public boolean isValid(String sessionId) {
        return sessionId != null && VALID_SESSION.matcher(sessionId).matches();
    }
}
