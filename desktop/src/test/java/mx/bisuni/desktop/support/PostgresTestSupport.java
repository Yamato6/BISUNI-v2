package mx.bisuni.desktop.support;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import mx.bisuni.desktop.config.AppConfig;
import mx.bisuni.desktop.db.Database;
import org.flywaydb.core.Flyway;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Postgres embebido compartido por todas las pruebas de la JVM.
 * <p>
 * Al arrancar aplica con Flyway el esquema y el seed reales del repositorio ({@code db/migrations}
 * y {@code db/seed}) en una base plantilla. Cada prueba recibe una base nueva clonada de la
 * plantilla ({@code CREATE DATABASE ... TEMPLATE}), así no se contaminan entre sí.
 */
public final class PostgresTestSupport {

    private static final String TEMPLATE = "bisuni_template";
    private static final AtomicInteger COUNTER = new AtomicInteger();
    private static EmbeddedPostgres pg;

    private PostgresTestSupport() {
    }

    public static Path repoRoot() {
        return AppConfig.findRepoRoot(Path.of("").toAbsolutePath())
                .orElseThrow(() -> new IllegalStateException("No se encontró db/migrations subiendo desde el directorio actual"));
    }

    /** Aplica migraciones + seed del repositorio sobre {@code ds}. */
    public static void migrate(DataSource ds) {
        Path root = repoRoot();
        Flyway.configure()
                .dataSource(ds)
                .locations("filesystem:" + root.resolve("db/migrations"), "filesystem:" + root.resolve("db/seed"))
                .load()
                .migrate();
    }

    private static synchronized EmbeddedPostgres server() throws SQLException {
        if (pg == null) {
            try {
                pg = EmbeddedPostgres.builder().start();
            } catch (IOException e) {
                throw new IllegalStateException("No se pudo iniciar el Postgres embebido", e);
            }
            exec(pg.getPostgresDatabase(), "CREATE DATABASE " + TEMPLATE);
            migrate(pg.getDatabase("postgres", TEMPLATE));
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    pg.close();
                } catch (IOException ignored) {
                    // salida de la JVM
                }
            }));
        }
        return pg;
    }

    /** Base nueva con esquema y seed, exclusiva para quien la pide. */
    public static Database freshDatabase() {
        try {
            EmbeddedPostgres server = server();
            String name = "bisuni_t" + COUNTER.incrementAndGet();
            exec(server.getPostgresDatabase(), "CREATE DATABASE " + name + " TEMPLATE " + TEMPLATE);
            return new Database(server.getDatabase("postgres", name));
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void exec(DataSource ds, String sql) throws SQLException {
        try (Connection c = ds.getConnection(); Statement st = c.createStatement()) {
            st.execute(sql);
        }
    }
}
