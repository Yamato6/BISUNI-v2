package mx.bisuni.desktop.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import mx.bisuni.desktop.config.AppConfig;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/** Punto de acceso a la base: pool de conexiones y helpers de transacción. */
public final class Database implements AutoCloseable {

    @FunctionalInterface
    public interface SqlWork<T> {
        T run(Connection conn) throws SQLException;
    }

    private final DataSource dataSource;
    /** id_user de quien opera (bitácora); {@code null} = sin actor. */
    private final Integer actor;
    /** Solo la instancia original cierra el pool; las vistas de {@link #actor(int)} no. */
    private final boolean owner;

    public Database(DataSource dataSource) {
        this(dataSource, null, true);
    }

    private Database(DataSource dataSource, Integer actor, boolean owner) {
        this.dataSource = dataSource;
        this.actor = actor;
        this.owner = owner;
    }

    /**
     * Vista de esta base que identifica al usuario en sesión: cada conexión fija la variable
     * {@code bisuni.actor}, que leen los triggers de la bitácora ({@code audit_log}).
     */
    public Database actor(int idUser) {
        return new Database(dataSource, idUser, false);
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
        try (Connection conn = open()) {
            try {
                return work.run(conn);
            } finally {
                release(conn);
            }
        }
    }

    /** Ejecuta {@code work} en una transacción: commit si termina bien, rollback ante cualquier excepción. */
    public <T> T inTransaction(SqlWork<T> work) throws SQLException {
        try (Connection conn = open()) {
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
                release(conn);
            }
        }
    }

    private Connection open() throws SQLException {
        Connection conn = dataSource.getConnection();
        if (actor != null) {
            try {
                setActor(conn, actor.toString());
            } catch (SQLException e) {
                conn.close();
                throw e;
            }
        }
        return conn;
    }

    /** La conexión vuelve al pool: que no conserve el actor de esta operación. */
    private void release(Connection conn) throws SQLException {
        if (actor != null) {
            setActor(conn, "");
        }
    }

    private static void setActor(Connection conn, String value) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT set_config('bisuni.actor', ?, false)")) {
            ps.setString(1, value);
            ps.execute();
        }
    }

    @Override
    public void close() {
        if (owner && dataSource instanceof AutoCloseable c) {
            try {
                c.close();
            } catch (Exception ignored) {
                // cierre en salida de la app
            }
        }
    }
}
