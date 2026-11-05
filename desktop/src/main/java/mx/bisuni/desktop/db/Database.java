package mx.bisuni.desktop.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import mx.bisuni.desktop.config.AppConfig;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

/** Punto de acceso a la base: pool de conexiones y helpers de transacción. */
public final class Database implements AutoCloseable {

    @FunctionalInterface
    public interface SqlWork<T> {
        T run(Connection conn) throws SQLException;
    }

    private final DataSource dataSource;

    public Database(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public static Database connect(AppConfig config) {
        HikariConfig hc = new HikariConfig();
        hc.setJdbcUrl(config.jdbcUrl());
        hc.setUsername(config.user());
        hc.setPassword(config.password());
        hc.setPoolName("bisuni");
        hc.setMaximumPoolSize(5);
        // No fallar al arrancar si la BD no está lista: el Login mostrará el error al intentar entrar.
        hc.setInitializationFailTimeout(-1);
        hc.setConnectionTimeout(5_000);
        return new Database(new HikariDataSource(hc));
    }

    /** Ejecuta {@code work} con una conexión en autocommit. */
    public <T> T withConnection(SqlWork<T> work) throws SQLException {
        try (Connection conn = dataSource.getConnection()) {
            return work.run(conn);
        }
    }

    /** Ejecuta {@code work} en una transacción: commit si termina bien, rollback ante cualquier excepción. */
    public <T> T inTransaction(SqlWork<T> work) throws SQLException {
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try {
                T result = work.run(conn);
                conn.commit();
                return result;
            } catch (SQLException | RuntimeException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    @Override
    public void close() {
        if (dataSource instanceof AutoCloseable c) {
            try {
                c.close();
            } catch (Exception ignored) {
                // cierre en salida de la app
            }
        }
    }
}
