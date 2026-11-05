package mx.bisuni.desktop.dao;

import mx.bisuni.desktop.model.RecordStatus;
import mx.bisuni.desktop.model.Role;
import mx.bisuni.desktop.model.UserData;
import mx.bisuni.desktop.model.UserRow;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
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

    private static final String SELECT_ROW = """
            SELECT id_user, username, first_name, last_name, email, role::text AS role,
                   status::text AS status, created_at
            FROM users
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

    /** Lista usuarios; cualquier filtro en {@code null} no se aplica. */
    public List<UserRow> list(Connection conn, Role role, RecordStatus status, String search) throws SQLException {
        String sql = SELECT_ROW + """
                WHERE (?::user_role IS NULL OR role = ?::user_role)
                  AND (?::record_status IS NULL OR status = ?::record_status)
                  AND (?::text IS NULL OR username ILIKE ? ESCAPE '\\' OR email ILIKE ? ESCAPE '\\'
                       OR (first_name || ' ' || last_name) ILIKE ? ESCAPE '\\')
                ORDER BY last_name, first_name, id_user
                """;
        String pattern = Sql.containsPattern(search);
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, Sql.name(role));
            ps.setString(2, Sql.name(role));
            ps.setString(3, Sql.name(status));
            ps.setString(4, Sql.name(status));
            for (int i = 5; i <= 8; i++) {
                ps.setString(i, pattern);
            }
            List<UserRow> rows = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(mapRow(rs));
                }
            }
            return rows;
        }
    }

    public Optional<UserRow> findById(Connection conn, int idUser) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(SELECT_ROW + " WHERE id_user = ?")) {
            ps.setInt(1, idUser);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        }
    }

    /** Inserta el usuario y devuelve su {@code id_user}. Los datos ya deben venir normalizados. */
    public int insert(Connection conn, UserData d, Role role, String passwordHash) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO users (username, password_hash, email, first_name, last_name, role, status)
                VALUES (?, ?, ?, ?, ?, ?::user_role, ?::record_status)
                RETURNING id_user
                """)) {
            ps.setString(1, d.username());
            ps.setString(2, passwordHash);
            ps.setString(3, d.email());
            ps.setString(4, d.firstName());
            ps.setString(5, d.lastName());
            ps.setString(6, role.name());
            ps.setString(7, d.status().name());
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    /** Actualiza los datos editables; devuelve {@code false} si el usuario no existe. */
    public boolean update(Connection conn, int idUser, UserData d) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
                UPDATE users SET username = ?, email = ?, first_name = ?, last_name = ?, status = ?::record_status
                WHERE id_user = ?
                """)) {
            ps.setString(1, d.username());
            ps.setString(2, d.email());
            ps.setString(3, d.firstName());
            ps.setString(4, d.lastName());
            ps.setString(5, d.status().name());
            ps.setInt(6, idUser);
            return ps.executeUpdate() == 1;
        }
    }

    public void updatePassword(Connection conn, int idUser, String passwordHash) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("UPDATE users SET password_hash = ? WHERE id_user = ?")) {
            ps.setString(1, passwordHash);
            ps.setInt(2, idUser);
            ps.executeUpdate();
        }
    }

    public void insertAdminProfile(Connection conn, int idUser, String position) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO admin_profile (id_user, position) VALUES (?, ?)")) {
            ps.setInt(1, idUser);
            ps.setString(2, position);
            ps.executeUpdate();
        }
    }

    public void insertTeacherProfile(Connection conn, int idUser, String specialty, String phone) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO teacher_profile (id_user, specialty, phone) VALUES (?, ?, ?)")) {
            ps.setInt(1, idUser);
            ps.setString(2, specialty);
            ps.setString(3, phone);
            ps.executeUpdate();
        }
    }

    private static UserRow mapRow(ResultSet rs) throws SQLException {
        return new UserRow(
                rs.getInt("id_user"),
                rs.getString("username"),
                rs.getString("first_name"),
                rs.getString("last_name"),
                rs.getString("email"),
                Role.valueOf(rs.getString("role")),
                RecordStatus.valueOf(rs.getString("status")),
                rs.getObject("created_at", OffsetDateTime.class));
    }
}
