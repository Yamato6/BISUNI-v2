package mx.bisuni.desktop.dao;

import mx.bisuni.desktop.model.RecordStatus;
import mx.bisuni.desktop.model.StudentRow;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class StudentDao {

    /** Filtros de la lista de alumnos. {@code null} = no filtrar; {@code withoutGroup} = solo sin grupo. */
    public record Filter(Integer idCareer, Integer idGroup, boolean withoutGroup, RecordStatus status, String search) {
        public static Filter none() {
            return new Filter(null, null, false, null, null);
        }
    }

    private static final String SELECT = """
            SELECT s.id_student, u.id_user, u.username, u.first_name, u.last_name, u.email,
                   u.status::text AS status, s.id_career, c.career_name, s.semester, s.gpa,
                   s.id_group, g.group_name
            FROM student_profile s
            JOIN users  u ON u.id_user   = s.id_user
            JOIN career c ON c.id_career = s.id_career
            LEFT JOIN school_groups g ON g.id_group = s.id_group
            """;

    public List<StudentRow> list(Connection conn, Filter f) throws SQLException {
        String sql = SELECT + """
                WHERE (?::int IS NULL OR s.id_career = ?)
                  AND (?::int IS NULL OR s.id_group = ?)
                  AND (NOT ? OR s.id_group IS NULL)
                  AND (?::record_status IS NULL OR u.status = ?::record_status)
                  AND (?::text IS NULL OR u.username ILIKE ? ESCAPE '\\' OR u.email ILIKE ? ESCAPE '\\'
                       OR (u.first_name || ' ' || u.last_name) ILIKE ? ESCAPE '\\')
                ORDER BY u.last_name, u.first_name, s.id_student
                """;
        String pattern = Sql.containsPattern(f.search());
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            setNullableInt(ps, 1, f.idCareer());
            setNullableInt(ps, 2, f.idCareer());
            setNullableInt(ps, 3, f.idGroup());
            setNullableInt(ps, 4, f.idGroup());
            ps.setBoolean(5, f.withoutGroup());
            ps.setString(6, Sql.name(f.status()));
            ps.setString(7, Sql.name(f.status()));
            for (int i = 8; i <= 11; i++) {
                ps.setString(i, pattern);
            }
            List<StudentRow> rows = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(map(rs));
                }
            }
            return rows;
        }
    }

    public Optional<StudentRow> findById(Connection conn, int idStudent) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(SELECT + " WHERE s.id_student = ?")) {
            ps.setInt(1, idStudent);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    public int insert(Connection conn, int idUser, int idCareer, int semester) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO student_profile (id_user, id_career, semester) VALUES (?, ?, ?) RETURNING id_student")) {
            ps.setInt(1, idUser);
            ps.setInt(2, idCareer);
            ps.setInt(3, semester);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    public void updateProfile(Connection conn, int idStudent, int idCareer, int semester, BigDecimal gpa,
                              Integer idGroup) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
                UPDATE student_profile SET id_career = ?, semester = ?, gpa = ?, id_group = ?
                WHERE id_student = ?
                """)) {
            ps.setInt(1, idCareer);
            ps.setInt(2, semester);
            ps.setBigDecimal(3, gpa);
            setNullableInt(ps, 4, idGroup);
            ps.setInt(5, idStudent);
            ps.executeUpdate();
        }
    }

    private static StudentRow map(ResultSet rs) throws SQLException {
        return new StudentRow(
                rs.getInt("id_student"),
                rs.getInt("id_user"),
                rs.getString("username"),
                rs.getString("first_name"),
                rs.getString("last_name"),
                rs.getString("email"),
                RecordStatus.valueOf(rs.getString("status")),
                rs.getInt("id_career"),
                rs.getString("career_name"),
                rs.getInt("semester"),
                rs.getBigDecimal("gpa"),
                rs.getObject("id_group", Integer.class),
                rs.getString("group_name"));
    }

    static void setNullableInt(PreparedStatement ps, int index, Integer value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.INTEGER);
        } else {
            ps.setInt(index, value);
        }
    }
}
