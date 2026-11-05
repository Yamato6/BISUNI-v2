package mx.bisuni.desktop.support;

import mx.bisuni.desktop.db.Database;

import java.sql.PreparedStatement;
import java.sql.ResultSet;

/** Consultas rápidas para las aserciones de las pruebas. */
public final class Sql {
    private Sql() {
    }

    public static int count(Database db, String sql, Object... params) throws Exception {
        return Integer.parseInt(text(db, sql, params));
    }

    public static Integer intOrNull(Database db, String sql, Object... params) throws Exception {
        String v = text(db, sql, params);
        return v == null ? null : Integer.valueOf(v);
    }

    public static String text(Database db, String sql, Object... params) throws Exception {
        return db.withConnection(c -> {
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                for (int i = 0; i < params.length; i++) {
                    ps.setObject(i + 1, params[i]);
                }
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw new IllegalStateException("Sin filas: " + sql);
                    }
                    return rs.getString(1);
                }
            }
        });
    }

    public static void exec(Database db, String sql) throws Exception {
        db.withConnection(c -> c.createStatement().executeUpdate(sql));
    }
}
