package mx.bisuni.desktop.dao;

import mx.bisuni.desktop.model.Role;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

public final class UserDao {

    /** Usuario con su hash y, si los tiene, sus perfiles de admin/profesor. */
    public record Credentials(
            int idUser,
            String email,
            String firstName,
            String lastName,
            Role role,
            boolean active,
            String passwordHash,
            Integer idAdmin,
            Integer idTeacher) {
    }

    private static final String FIND_BY_EMAIL = """
            SELECT u.id_user, u.email, u.first_name, u.last_name, u.role::text AS role,
                   u.status::text AS status, u.password_hash, a.id_admin, t.id_teacher
            FROM users u
            LEFT JOIN admin_profile   a ON a.id_user = u.id_user
            LEFT JOIN teacher_profile t ON t.id_user = u.id_user
            WHERE u.email = ?
            """;

    /** Busca por email ya normalizado (minúsculas, sin espacios). */
    public Optional<Credentials> findByEmail(Connection conn, String normalizedEmail) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(FIND_BY_EMAIL)) {
            ps.setString(1, normalizedEmail);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(new Credentials(
                        rs.getInt("id_user"),
                        rs.getString("email"),
                        rs.getString("first_name"),
                        rs.getString("last_name"),
                        Role.valueOf(rs.getString("role")),
                        "Active".equals(rs.getString("status")),
                        rs.getString("password_hash"),
                        rs.getObject("id_admin", Integer.class),
                        rs.getObject("id_teacher", Integer.class)));
            }
        }
    }

    public void touchAdminLastAccess(Connection conn, int idAdmin) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE admin_profile SET last_access = now() WHERE id_admin = ?")) {
            ps.setInt(1, idAdmin);
            ps.executeUpdate();
        }
    }
}
