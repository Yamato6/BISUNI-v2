package mx.bisuni.desktop.dao;

import mx.bisuni.desktop.model.Period;
import mx.bisuni.desktop.model.PeriodData;
import mx.bisuni.desktop.model.PeriodStatus;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class PeriodDao {

    private static final String SELECT = """
            SELECT p.id_period, p.name, p.start_date, p.end_date, p.status::text AS status, p.admissions_open,
                   (SELECT count(*) FROM school_groups g WHERE g.id_period = p.id_period) AS group_count
            FROM academic_period p
            """;

    /** Todos los periodos, del más reciente al más antiguo. */
    public List<Period> list(Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(SELECT + " ORDER BY p.start_date DESC");
             ResultSet rs = ps.executeQuery()) {
            List<Period> out = new ArrayList<>();
            while (rs.next()) {
                out.add(map(rs));
            }
            return out;
        }
    }

    public Optional<Period> findById(Connection conn, int idPeriod) throws SQLException {
        return one(conn, SELECT + " WHERE p.id_period = ?", idPeriod);
    }

    /** Periodo del grupo. */
    public Optional<Period> ofGroup(Connection conn, int idGroup) throws SQLException {
        return one(conn, SELECT + " JOIN school_groups sg ON sg.id_period = p.id_period WHERE sg.id_group = ?", idGroup);
    }

    public Optional<Period> active(Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(SELECT + " WHERE p.status = 'Active'");
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? Optional.of(map(rs)) : Optional.empty();
        }
    }

    public int insert(Connection conn, PeriodData d) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO academic_period (name, start_date, end_date) VALUES (?, ?, ?) RETURNING id_period")) {
            ps.setString(1, d.name());
            ps.setDate(2, Date.valueOf(d.startDate()));
            ps.setDate(3, Date.valueOf(d.endDate()));
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    public void update(Connection conn, int idPeriod, PeriodData d) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE academic_period SET name = ?, start_date = ?, end_date = ? WHERE id_period = ?")) {
            ps.setString(1, d.name());
            ps.setDate(2, Date.valueOf(d.startDate()));
            ps.setDate(3, Date.valueOf(d.endDate()));
            ps.setInt(4, idPeriod);
            ps.executeUpdate();
        }
    }

    /** Cambia el estatus; al cerrar también se cierra la convocatoria. */
    public void setStatus(Connection conn, int idPeriod, PeriodStatus status) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
                UPDATE academic_period
                SET status = ?::period_status,
                    admissions_open = CASE WHEN ?::period_status = 'Closed' THEN false ELSE admissions_open END
                WHERE id_period = ?
                """)) {
            ps.setString(1, status.name());
            ps.setString(2, status.name());
            ps.setInt(3, idPeriod);
            ps.executeUpdate();
        }
    }

    public void setAdmissionsOpen(Connection conn, int idPeriod, boolean open) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE academic_period SET admissions_open = ? WHERE id_period = ?")) {
            ps.setBoolean(1, open);
            ps.setInt(2, idPeriod);
            ps.executeUpdate();
        }
    }

    /** Nombre del otro periodo cuyas fechas se traslapan con [start, end], si lo hay. */
    public Optional<String> overlapping(Connection conn, PeriodData d, Integer excludeId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
                SELECT name FROM academic_period
                WHERE daterange(start_date, end_date, '[]') && daterange(?, ?, '[]')
                  AND (?::int IS NULL OR id_period <> ?)
                ORDER BY start_date LIMIT 1
                """)) {
            ps.setDate(1, Date.valueOf(d.startDate()));
            ps.setDate(2, Date.valueOf(d.endDate()));
            StudentDao.setNullableInt(ps, 3, excludeId);
            StudentDao.setNullableInt(ps, 4, excludeId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(rs.getString(1)) : Optional.empty();
            }
        }
    }

    /** Actas abiertas del periodo, como "IT-1A · Programming Fundamentals". */
    public List<String> openActas(Connection conn, int idPeriod) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
                SELECT g.group_name || ' · ' || s.subject_name
                FROM group_subject gs
                JOIN school_groups g ON g.id_group = gs.id_group
                JOIN subjects s ON s.id_subject = gs.id_subject
                WHERE g.id_period = ? AND gs.grades_closed_at IS NULL
                ORDER BY g.group_name, s.subject_name
                """)) {
            ps.setInt(1, idPeriod);
            List<String> out = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(rs.getString(1));
                }
            }
            return out;
        }
    }

    private static Optional<Period> one(Connection conn, String sql, int id) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    private static Period map(ResultSet rs) throws SQLException {
        return new Period(
                rs.getInt("id_period"),
                rs.getString("name"),
                rs.getDate("start_date").toLocalDate(),
                rs.getDate("end_date").toLocalDate(),
                PeriodStatus.valueOf(rs.getString("status")),
                rs.getBoolean("admissions_open"),
                rs.getInt("group_count"));
    }
}
