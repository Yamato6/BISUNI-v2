package mx.bisuni.desktop.dev;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import mx.bisuni.desktop.config.AppConfig;
import mx.bisuni.desktop.config.ConfigException;
import mx.bisuni.desktop.support.PostgresTestSupport;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Comparator;
import java.util.concurrent.CountDownLatch;
import java.util.stream.Stream;

/**
 * Postgres de desarrollo sin Docker, para probar la app a mano.
 * <p>
 * Levanta un PostgreSQL 17 embebido en {@code DB_PORT} (5432 por defecto) con datos persistentes
 * en {@code %TEMP%/bisuni-devdb}, crea el usuario y la base de {@code .env} y aplica con Flyway
 * {@code db/migrations} + {@code db/seed}. Se queda corriendo hasta Ctrl+C.
 * <pre>
 *   mvnw exec:java                               # arranca (o reanuda) la BD
 *   mvnw exec:java -Dexec.args=--reset           # la borra y la recrea desde cero
 * </pre>
 */
public final class DevDatabase {

    public static void main(String[] args) throws Exception {
        AppConfig cfg;
        try {
            cfg = AppConfig.load();
        } catch (ConfigException e) {
            System.err.println(e.getMessage());
            System.err.println("Copia .env.example a .env en la raíz del repositorio y define DB_PASSWORD.");
            System.exit(1);
            return;
        }
        Path dataDir = Path.of(System.getProperty("java.io.tmpdir"), "bisuni-devdb");
        boolean reset = args.length > 0 && args[0].equals("--reset");
        if (reset) {
            deleteRecursively(dataDir);
        }
        Files.createDirectories(dataDir);

        EmbeddedPostgres pg;
        try {
            pg = EmbeddedPostgres.builder()
                    .setPort(cfg.port())
                    .setDataDirectory(dataDir)
                    .setCleanDataDirectory(false)
                    .start();
        } catch (IllegalStateException e) {
            if (String.valueOf(e.getMessage()).contains("could not lock")) {
                System.err.println("Ya hay una BD de desarrollo corriendo (en otra terminal) con los datos de "
                        + dataDir + ".\nUsa esa, o detenla con Ctrl+C antes de arrancar otra.");
                System.exit(1);
            }
            throw e;
        }

        try (Connection c = pg.getPostgresDatabase().getConnection()) {
            if (!exists(c, "SELECT 1 FROM pg_roles WHERE rolname = ?", cfg.user())) {
                try (Statement st = c.createStatement()) {
                    st.execute("CREATE ROLE \"" + cfg.user() + "\" LOGIN PASSWORD '"
                            + cfg.password().replace("'", "''") + "'");
                }
            }
            if (!exists(c, "SELECT 1 FROM pg_database WHERE datname = ?", cfg.database())) {
                try (Statement st = c.createStatement()) {
                    st.execute("CREATE DATABASE \"" + cfg.database() + "\" OWNER \"" + cfg.user() + "\"");
                }
            }
        }
        PostgresTestSupport.migrate(pg.getDatabase(cfg.user(), cfg.database()));

        System.out.println();
        System.out.println("==============================================================");
        System.out.println(" BD de desarrollo lista en " + cfg.jdbcUrl());
        System.out.println(" Datos en " + dataDir + (reset ? " (recreada)" : ""));
        System.out.println(" Cuentas de prueba (password123): admin.master@bisuni.edu, juan.perez@bisuni.edu");
        System.out.println(" Ctrl+C para detenerla.");
        System.out.println("==============================================================");

        CountDownLatch stop = new CountDownLatch(1);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                pg.close();
            } catch (IOException ignored) {
                // apagando
            }
            stop.countDown();
        }));
        stop.await();
    }

    private static boolean exists(Connection c, String sql, String value) throws Exception {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, value);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static void deleteRecursively(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            for (Path p : walk.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(p);
            }
        }
    }
}
