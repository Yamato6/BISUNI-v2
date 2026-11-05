package mx.bisuni.desktop.dao;

import mx.bisuni.desktop.model.RecordStatus;
import mx.bisuni.desktop.model.TeacherRow;
import mx.bisuni.desktop.model.TeacherSubject;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class TeacherDao {

    private static final String SELECT = """
            SELECT t.id_teacher, u.id_user, u.username, u.first_name, u.last_name, u.email,
                   u.status::text AS status, t.specialty, t.phone,
                   (SELECT count(*) FROM teacher_subject ts WHERE ts.id_teacher = t.id_teacher) AS subject_count
            FROM teacher_profile t
            JOIN users u ON u.id_user = t.id_user
            """;

    public List<TeacherRow> list(Connection conn, RecordStatus status, String search) throws SQLException {
        String sql = SELECT + """
                WHERE (?::record_status IS NULL OR u.status = ?::record_status)
                  AND (?::text IS NULL OR u.username ILIKE ? ESCAPE '\\' OR u.email ILIKE ? ESCAPE '\\'
                       OR (u.first_name || ' ' || u.last_name) ILIKE ? ESCAPE '\\'
                       OR t.specialty ILIKE ? ESCAPE '\\')
                ORDER BY u.last_name, u.first_name, t.id_teacher
                """;
        String pattern = Sql.containsPattern(search);
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, Sql.name(status));
            ps.setString(2, Sql.name(status));
            for (int i = 3; i <= 7; i++) {
                ps.setString(i, pattern);
            }
            List<TeacherRow> rows = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(map(rs));
                }
            }
            return rows;
        }
    }

    public Optional<TeacherRow> findById(Connection conn, int idTeacher) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(SELECT + " WHERE t.id_teacher = ?")) {
            ps.setInt(1, idTeacher);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    public void updateProfile(Connection conn, int idTeacher, String specialty, String phone) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE teacher_profile SET specialty = ?, phone = ? WHERE id_teacher = ?")) {
            ps.setString(1, specialty);
            ps.setString(2, phone);
            ps.setInt(3, idTeacher);
            ps.executeUpdate();
        }
    }

    /** Materias que el profesor puede impartir, con los grupos donde ya las imparte. */
    public List<TeacherSubject> subjects(Connection conn, int idTeacher) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
                SELECT s.id_subject, s.subject_name, c.career_name, s.semester, s.credits,
                       coalesce((SELECT string_agg(g.group_name, ', ' ORDER BY g.group_name)
                                 FROM group_subject gs JOIN school_groups g ON g.id_group = gs.id_group
                                 WHERE gs.id_teacher = ts.id_teacher AND gs.id_subject = ts.id_subject), '') AS groups
                FROM teacher_subject ts
                JOIN subjects s ON s.id_subject = ts.id_subject
                JOIN career   c ON c.id_career  = s.id_career
                WHERE ts.id_teacher = ?
                ORDER BY c.career_name, s.semester, s.subject_name
                """)) {
            ps.setInt(1, idTeacher);
            List<TeacherSubject> out = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new TeacherSubject(rs.getInt("id_subject"), rs.getString("subject_name"),
                            rs.getString("career_name"), rs.getInt("semester"), rs.getInt("credits"),
                            rs.getString("groups")));
                }
            }
            return out;
        }
    }

    public void addSubject(Connection conn, int idTeacher, int idSubject) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO teacher_subject (id_teacher, id_subject) VALUES (?, ?)")) {
            ps.setInt(1, idTeacher);
            ps.setInt(2, idSubject);
            ps.executeUpdate();
        }
    }

    /** Devuelve {@code false} si la relación no existía. */
    public boolean removeSubject(Connection conn, int idTeacher, int idSubject) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "DELETE FROM teacher_subject WHERE id_teacher = ? AND id_subject = ?")) {
            ps.setInt(1, idTeacher);
            ps.setInt(2, idSubject);
            return ps.executeUpdate() == 1;
        }
    }

    private static TeacherRow map(ResultSet rs) throws SQLException {
        return new TeacherRow(
                rs.getInt("id_teacher"),
                rs.getInt("id_user"),
                rs.getString("username"),
                rs.getString("first_name"),
                rs.getString("last_name"),
                rs.getString("email"),
                RecordStatus.valueOf(rs.getString("status")),
                rs.getString("specialty"),
                rs.getString("phone"),
                rs.getInt("subject_count"));
    }
}
