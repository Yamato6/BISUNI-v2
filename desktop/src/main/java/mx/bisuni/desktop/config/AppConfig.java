package mx.bisuni.desktop.config;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

/**
 * Configuración de conexión. Precedencia (gana la primera que defina la clave):
 * <ol>
 *   <li>Variables de entorno ({@code DB_HOST}, {@code DB_PORT}, {@code DB_NAME}, {@code DB_USER}, {@code DB_PASSWORD}).</li>
 *   <li>{@code desktop/bisuni.properties} (ignorado por git), con las mismas claves.</li>
 *   <li>{@code .env} en la raíz del repositorio.</li>
 * </ol>
 */
public record AppConfig(String host, int port, String database, String user, String password) {

    static final List<String> KEYS = List.of("DB_HOST", "DB_PORT", "DB_NAME", "DB_USER", "DB_PASSWORD");

    public String jdbcUrl() {
        return "jdbc:postgresql://" + host + ":" + port + "/" + database;
    }

    /** Carga la configuración desde las fuentes estándar. */
    public static AppConfig load() {
        Map<String, String> values = new HashMap<>();
        Optional<Path> repoRoot = findRepoRoot(Path.of("").toAbsolutePath());

        // Se aplican de menor a mayor prioridad; cada fuente sobrescribe a la anterior.
        repoRoot.map(r -> r.resolve(".env")).ifPresent(p -> values.putAll(readEnvFile(p)));
        candidatePropertiesFiles(repoRoot).forEach(p -> values.putAll(readProperties(p)));
        for (String key : KEYS) {
            String env = System.getenv(key);
            if (env != null && !env.isBlank()) {
                values.put(key, env);
            }
        }
        return fromMap(values);
    }

    static AppConfig fromMap(Map<String, String> v) {
        String password = v.get("DB_PASSWORD");
        if (password == null || password.isBlank()) {
            throw new ConfigException(
                    "Falta DB_PASSWORD. Defínela en desktop/bisuni.properties, en el .env de la raíz "
                            + "del repositorio o como variable de entorno.");
        }
        int port;
        try {
            port = Integer.parseInt(v.getOrDefault("DB_PORT", "5432").trim());
        } catch (NumberFormatException e) {
            throw new ConfigException("DB_PORT no es un número válido: " + v.get("DB_PORT"));
        }
        return new AppConfig(
                v.getOrDefault("DB_HOST", "localhost").trim(),
                port,
                v.getOrDefault("DB_NAME", "bisuni").trim(),
                v.getOrDefault("DB_USER", "bisuni").trim(),
                password);
    }

    /** Sube desde {@code start} hasta encontrar la carpeta que contiene {@code db/migrations}. */
    public static Optional<Path> findRepoRoot(Path start) {
        for (Path p = start; p != null; p = p.getParent()) {
            if (Files.isDirectory(p.resolve("db").resolve("migrations"))) {
                return Optional.of(p);
            }
        }
        return Optional.empty();
    }

    private static List<Path> candidatePropertiesFiles(Optional<Path> repoRoot) {
        Path inCwd = Path.of("bisuni.properties").toAbsolutePath();
        Path inDesktop = repoRoot.map(r -> r.resolve("desktop").resolve("bisuni.properties")).orElse(inCwd);
        return inCwd.equals(inDesktop) ? List.of(inCwd) : List.of(inDesktop, inCwd);
    }

    private static Map<String, String> readProperties(Path file) {
        Map<String, String> out = new HashMap<>();
        if (!Files.isRegularFile(file)) {
            return out;
        }
        Properties props = new Properties();
        try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            props.load(r);
        } catch (IOException e) {
            throw new ConfigException("No se pudo leer " + file + ": " + e.getMessage());
        }
        for (String key : KEYS) {
            String value = props.getProperty(key);
            if (value != null && !value.isBlank()) {
                out.put(key, value.trim());
            }
        }
        return out;
    }

    /** Lector mínimo de .env: líneas CLAVE=valor, ignora comentarios y comillas envolventes. */
    static Map<String, String> readEnvFile(Path file) {
        Map<String, String> out = new HashMap<>();
        if (!Files.isRegularFile(file)) {
            return out;
        }
        List<String> lines;
        try {
            lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new ConfigException("No se pudo leer " + file + ": " + e.getMessage());
        }
        for (String raw : lines) {
            String line = raw.strip();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            if (line.startsWith("export ")) {
                line = line.substring("export ".length()).strip();
            }
            int eq = line.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String key = line.substring(0, eq).strip();
            String value = line.substring(eq + 1).strip();
            if (value.length() >= 2
                    && ((value.startsWith("\"") && value.endsWith("\""))
                    || (value.startsWith("'") && value.endsWith("'")))) {
                value = value.substring(1, value.length() - 1);
            }
            if (KEYS.contains(key) && !value.isEmpty()) {
                out.put(key, value);
            }
        }
        return out;
    }
}
