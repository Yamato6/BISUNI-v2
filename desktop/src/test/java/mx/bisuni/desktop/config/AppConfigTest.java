package mx.bisuni.desktop.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppConfigTest {

    @Test
    void readsEnvFileIgnoringCommentsAndQuotes(@TempDir Path dir) throws Exception {
        Path env = dir.resolve(".env");
        Files.writeString(env, """
                # comentario
                DB_NAME=bisuni
                export DB_USER="bisuni"
                DB_PASSWORD='s3cr=to'
                DB_PORT = 5433
                AUTH_SECRET=no-me-interesa
                """);
        Map<String, String> v = AppConfig.readEnvFile(env);
        assertEquals("bisuni", v.get("DB_USER"));
        assertEquals("s3cr=to", v.get("DB_PASSWORD"));
        assertEquals("5433", v.get("DB_PORT"));
        assertTrue(!v.containsKey("AUTH_SECRET"));

        AppConfig cfg = AppConfig.fromMap(v);
        assertEquals("jdbc:postgresql://localhost:5433/bisuni", cfg.jdbcUrl());
    }

    @Test
    void missingPasswordGivesClearError() {
        var e = assertThrows(ConfigException.class, () -> AppConfig.fromMap(Map.of("DB_USER", "x")));
        assertTrue(e.getMessage().contains("DB_PASSWORD"));
    }

    @Test
    void invalidPortGivesClearError() {
        assertThrows(ConfigException.class,
                () -> AppConfig.fromMap(Map.of("DB_PASSWORD", "x", "DB_PORT", "abc")));
    }
}
