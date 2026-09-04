package com.fashion.integration;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;
import java.util.Locale;

final class B7MigrationRunner {
    private B7MigrationRunner() {
    }

    static void run(String jdbcUrl, String username, String password) throws Exception {
        Path path = Paths.get("..", "..", "mysql", "add_review_integrity.sql");
        List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        String delimiter = ";";
        StringBuilder statement = new StringBuilder();
        try (Connection connection = DriverManager.getConnection(jdbcUrl, username, password);
             Statement jdbc = connection.createStatement()) {
            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("--")) {
                    continue;
                }
                if (trimmed.toUpperCase(Locale.ROOT).startsWith("DELIMITER ")) {
                    delimiter = trimmed.substring("DELIMITER ".length()).trim();
                    continue;
                }
                statement.append(line).append('\n');
                if (trimmed.endsWith(delimiter)) {
                    String sql = statement.toString().trim();
                    sql = sql.substring(0, sql.length() - delimiter.length()).trim();
                    jdbc.execute(sql);
                    statement.setLength(0);
                }
            }
        }
    }
}
