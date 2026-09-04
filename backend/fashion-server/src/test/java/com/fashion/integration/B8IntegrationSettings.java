package com.fashion.integration;

import org.yaml.snakeyaml.Yaml;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

final class B8IntegrationSettings {
    private B8IntegrationSettings() { }

    static Map<String, Object> section(String name) throws Exception {
        String configured = System.getProperty("b8." + name + ".config");
        if (configured == null || configured.trim().isEmpty()) {
            configured = System.getProperty("b8.config");
        }
        if (configured == null || configured.trim().isEmpty()) {
            throw new IllegalStateException("b8." + name + ".config or b8.config is required");
        }
        Path path = Paths.get(configured);
        if (!Files.isRegularFile(path)) throw new IllegalStateException("B8 config is missing");
        try (InputStream input = Files.newInputStream(path)) {
            Map<String, Object> root = new Yaml().load(input);
            return nested(nested(root, "fashion"), name);
        }
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> nested(Map<String, Object> source, String key) {
        Object value = source == null ? null : source.get(key);
        if (!(value instanceof Map)) throw new IllegalStateException("missing B8 config section: " + key);
        return (Map<String, Object>) value;
    }

    static String value(Map<String, Object> source, String key) {
        Object value = source.get(key);
        return value == null ? "" : value.toString();
    }

    static String exclusive(String dependency, Map<String, Object> source) {
        String explicit = System.getProperty("b8." + dependency + "-exclusive");
        return explicit == null || explicit.trim().isEmpty() ? value(source, "exclusive") : explicit;
    }

    static void requireLoopback(String host, String dependency) {
        if (!("127.0.0.1".equals(host) || "localhost".equalsIgnoreCase(host))) {
            throw new IllegalStateException(dependency + " integration endpoint must be loopback");
        }
    }
}
