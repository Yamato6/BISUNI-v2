package mx.bisuni.desktop.dao;

import mx.bisuni.desktop.model.CareerData;
import mx.bisuni.desktop.model.CareerRow;
import mx.bisuni.desktop.model.RecordStatus;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class CareerDao {

    private static final String SELECT = """
            SELECT c.id_career, c.career_name, c.code, c.total_semesters, c.status::text AS status,
                   (SELECT count(*) FROM student_profile sp WHERE sp.id_career = c.id_career) AS student_count,
                   (SELECT count(*) FROM subjects s WHERE s.id_career = c.id_career) AS subject_count,
                   (SELECT coalesce(max(s.semester), 0) FROM subjects s WHERE s.id_career = c.id_career) AS max_semester
            FROM career c
            """;

    public List<CareerRow> list(Connection conn, RecordStatus status) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(SELECT
                + " WHERE (?::record_status IS NULL OR c.status = ?::record_status) ORDER BY c.career_name")) {
            ps.setString(1, Sql.name(status));
            ps.setString(2, Sql.name(status));
            List<CareerRow> out = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(map(rs));
                }
            }
            return out;
        }
    }

    public Optional<CareerRow> findById(Connection conn, int idCareer) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(SELECT + " WHERE c.id_career = ?")) {
            ps.setInt(1, idCareer);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    public int insert(Connection conn, CareerData d) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO career (career_name, code, total_semesters, status)
                VALUES (?, ?, ?, ?::record_status) RETURNING id_career
                """)) {
            bind(ps, d);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    public void update(Connection conn, int idCareer, CareerData d) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
                UPDATE career SET career_name = ?, code = ?, total_semesters = ?, status = ?::record_status
                WHERE id_career = ?
                """)) {
            bind(ps, d);
            ps.setInt(5, idCareer);
            ps.executeUpdate();
        }
    }

    private static void bind(PreparedStatement ps, CareerData d) throws SQLException {
        ps.setString(1, d.name());
        ps.setString(2, d.code());
        ps.setInt(3, d.totalSemesters());
        ps.setString(4, d.status().name());
    }

    private static CareerRow map(ResultSet rs) throws SQLException {
        return new CareerRow(
                rs.getInt("id_career"),
                rs.getString("career_name"),
                rs.getString("code"),
                rs.getInt("total_semesters"),
                RecordStatus.valueOf(rs.getString("status")),
                rs.getInt("student_count"),
                rs.getInt("subject_count"),
                rs.getInt("max_semester"));
    }
}
