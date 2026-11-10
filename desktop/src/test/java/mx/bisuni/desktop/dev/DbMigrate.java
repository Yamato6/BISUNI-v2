package mx.bisuni.desktop.dev;

import mx.bisuni.desktop.config.AppConfig;
import mx.bisuni.desktop.config.ConfigException;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;

import java.nio.file.Path;

/**
 * Aplica con Flyway las migraciones (y, salvo {@code --sin-seed}, el seed de desarrollo) a la base
 * configurada en el .env: sirve para un PostgreSQL instalado en la máquina en lugar del embebido.
 * <pre>
 *   mvnw exec:java -Dexec.mainClass=mx.bisuni.desktop.dev.DbMigrate
 *   mvnw exec:java -Dexec.mainClass=mx.bisuni.desktop.dev.DbMigrate -Dexec.args=--sin-seed
 * </pre>
 * El seed empieza con TRUNCATE: en una base con datos reales, usar {@code --sin-seed}.
 */
public final class DbMigrate {

    public static void main(String[] args) {
        AppConfig cfg;
        try {
            cfg = AppConfig.load();
        } catch (ConfigException e) {
            System.err.println(e.getMessage());
            System.exit(1);
            return;
        }
        boolean withSeed = !(args.length > 0 && args[0].equals("--sin-seed"));
        Path root = AppConfig.findRepoRoot(Path.of("").toAbsolutePath())
                .orElseThrow(() -> new IllegalStateException("No se encontró db/migrations"));

        String[] locations = withSeed
                ? new String[]{"filesystem:" + root.resolve("db/migrations"), "filesystem:" + root.resolve("db/seed")}
                : new String[]{"filesystem:" + root.resolve("db/migrations")};

        System.out.println("Migrando " + cfg.jdbcUrl() + " como " + cfg.user() + (withSeed ? " (con seed)" : " (sin seed)"));
        MigrateResult result = Flyway.configure()
                .dataSource(cfg.jdbcUrl(), cfg.user(), cfg.password())
                .locations(locations)
                .load()
                .migrate();
        System.out.println("Listo: " + result.migrationsExecuted + " migraciones aplicadas"
                + (result.targetSchemaVersion != null ? "; esquema en la versión " + result.targetSchemaVersion : "") + ".");
    }
}
