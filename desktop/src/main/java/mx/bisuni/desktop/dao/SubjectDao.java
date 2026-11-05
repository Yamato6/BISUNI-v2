package mx.bisuni.desktop.dao;

import mx.bisuni.desktop.model.RecordStatus;
import mx.bisuni.desktop.model.SubjectData;
import mx.bisuni.desktop.model.SubjectRow;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class SubjectDao {

    public record Filter(Integer idCareer, Integer semester, RecordStatus status, String search) {
        public static Filter none() {
            return new Filter(null, null, null, null);
        }
    }

    private static final String SELECT = """
            SELECT s.id_subject, s.subject_name, s.id_career, c.career_name, s.semester, s.credits,
                   s.weekly_hours, s.status::text AS status,
                   (SELECT count(*) FROM group_subject gs WHERE gs.id_subject = s.id_subject) AS group_count,
                   (SELECT count(*) FROM teacher_subject ts WHERE ts.id_subject = s.id_subject) AS teacher_count
            FROM subjects s
            JOIN career c ON c.id_career = s.id_career
            """;

    public List<SubjectRow> list(Connection conn, Filter f) throws SQLException {
        String sql = SELECT + """
                WHERE (?::int IS NULL OR s.id_career = ?)
                  AND (?::int IS NULL OR s.semester = ?)
                  AND (?::record_status IS NULL OR s.status = ?::record_status)
                  AND (?::text IS NULL OR s.subject_name ILIKE ? ESCAPE '\\')
                ORDER BY c.career_name, s.semester, s.subject_name
                """;
        String pattern = Sql.containsPattern(f.search());
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            StudentDao.setNullableInt(ps, 1, f.idCareer());
            StudentDao.setNullableInt(ps, 2, f.idCareer());
            StudentDao.setNullableInt(ps, 3, f.semester());
            StudentDao.setNullableInt(ps, 4, f.semester());
            ps.setString(5, Sql.name(f.status()));
            ps.setString(6, Sql.name(f.status()));
            ps.setString(7, pattern);
            ps.setString(8, pattern);
            List<SubjectRow> rows = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(map(rs));
                }
            }
            return rows;
        }
    }

    public Optional<SubjectRow> findById(Connection conn, int idSubject) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(SELECT + " WHERE s.id_subject = ?")) {
            ps.setInt(1, idSubject);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    public int insert(Connection conn, SubjectData d) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO subjects (subject_name, credits, semester, weekly_hours, id_career, status)
                VALUES (?, ?, ?, ?, ?, ?::record_status) RETURNING id_subject
                """)) {
            bind(ps, d);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    public void update(Connection conn, int idSubject, SubjectData d) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
                UPDATE subjects SET subject_name = ?, credits = ?, semester = ?, weekly_hours = ?,
                                    id_career = ?, status = ?::record_status
                WHERE id_subject = ?
                """)) {
            bind(ps, d);
            ps.setInt(7, idSubject);
            ps.executeUpdate();
        }
    }

    private static void bind(PreparedStatement ps, SubjectData d) throws SQLException {
        ps.setString(1, d.name());
        ps.setInt(2, d.credits());
        ps.setInt(3, d.semester());
        ps.setInt(4, d.weeklyHours());
        ps.setInt(5, d.idCareer());
        ps.setString(6, d.status().name());
    }

    private static SubjectRow map(ResultSet rs) throws SQLException {
        return new SubjectRow(
                rs.getInt("id_subject"),
                rs.getString("subject_name"),
                rs.getInt("id_career"),
                rs.getString("career_name"),
                rs.getInt("semester"),
                rs.getInt("credits"),
                rs.getInt("weekly_hours"),
                RecordStatus.valueOf(rs.getString("status")),
                rs.getInt("group_count"),
                rs.getInt("teacher_count"));
    }
}
