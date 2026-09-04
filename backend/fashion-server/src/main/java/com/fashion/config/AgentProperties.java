package com.fashion.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;

@Component
@ConfigurationProperties(prefix = "fashion.agent")
public class AgentProperties {

    private static final int MAX_TIMEOUT_MS = 60000;

    private String baseUrl;
    private int connectTimeoutMs = 3000;
    private int readTimeoutMs = 10000;
    private String internalToken;
    private boolean allowInsecureHttp;
    private String chatUrl;

    public String validateAndGetChatUrl(String[] activeProfiles) {
        if (!StringUtils.hasText(baseUrl)) {
            throw new IllegalArgumentException("Agent base URL is required");
        }
        if (!StringUtils.hasText(internalToken) || internalToken.length() < 32
                || internalToken.chars().anyMatch(Character::isWhitespace)) {
            throw new IllegalArgumentException("Agent internal credential is invalid");
        }
        if (connectTimeoutMs <= 0 || connectTimeoutMs > MAX_TIMEOUT_MS
                || readTimeoutMs <= 0 || readTimeoutMs > MAX_TIMEOUT_MS) {
            throw new IllegalArgumentException("Agent HTTP timeouts must be between 1 and 60000 ms");
        }

        String normalized = baseUrl.trim().replaceAll("/+$", "");
        URI uri;
        try {
            uri = new URI(normalized);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Agent base URL is invalid");
        }
        if (!uri.isAbsolute() || uri.getHost() == null
                || !("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null) {
            throw new IllegalArgumentException("Agent base URL is invalid");
        }
        if (normalized.toLowerCase().endsWith("/chat")) {
            throw new IllegalArgumentException("Agent base URL must not include /chat");
        }

        boolean testProfile = containsProfile(activeProfiles, "test");
        boolean prodProfile = containsProfile(activeProfiles, "prod")
                || containsProfile(activeProfiles, "production");
        if (prodProfile && allowInsecureHttp) {
            throw new IllegalArgumentException("Insecure Agent HTTP is forbidden in production");
        }
        if ("http".equalsIgnoreCase(uri.getScheme()) && !isLoopbackHost(uri.getHost())
                && !(testProfile && allowInsecureHttp)) {
            throw new IllegalArgumentException("Non-loopback Agent URL must use HTTPS");
        }

        chatUrl = normalized + "/chat";
        return chatUrl;
    }

    private boolean containsProfile(String[] profiles, String expected) {
        return profiles != null && Arrays.stream(profiles).anyMatch(expected::equalsIgnoreCase);
    }

    private boolean isLoopbackHost(String host) {
        String normalizedHost = host.toLowerCase();
        if ("localhost".equals(normalizedHost) || "::1".equals(normalizedHost)
                || "[::1]".equals(normalizedHost)) {
            return true;
        }
        String[] octets = normalizedHost.split("\\.", -1);
        if (octets.length != 4 || !"127".equals(octets[0])) {
            return false;
        }
        try {
            for (int i = 1; i < octets.length; i++) {
                int value = Integer.parseInt(octets[i]);
                if (value < 0 || value > 255) {
                    return false;
                }
            }
            return true;
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    public String getChatUrl() {
        if (chatUrl == null) {
            throw new IllegalStateException("Agent properties have not been validated");
        }
        return chatUrl;
    }

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public int getConnectTimeoutMs() { return connectTimeoutMs; }
    public void setConnectTimeoutMs(int connectTimeoutMs) { this.connectTimeoutMs = connectTimeoutMs; }
    public int getReadTimeoutMs() { return readTimeoutMs; }
    public void setReadTimeoutMs(int readTimeoutMs) { this.readTimeoutMs = readTimeoutMs; }
    public String getInternalToken() { return internalToken; }
    public void setInternalToken(String internalToken) { this.internalToken = internalToken; }
    public boolean isAllowInsecureHttp() { return allowInsecureHttp; }
    public void setAllowInsecureHttp(boolean allowInsecureHttp) { this.allowInsecureHttp = allowInsecureHttp; }
}
