package mx.bisuni.desktop.dao;

import mx.bisuni.desktop.model.Dashboard;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** Consultas del panel de inicio. Las alertas se refieren al periodo activo. */
public final class DashboardDao {

    private static final String ACTIVE = "(SELECT id_period FROM academic_period WHERE status = 'Active')";

    public Dashboard load(Connection conn, BigDecimal riskPct, int topFailing) throws SQLException {
        String period = string(conn, "SELECT name FROM academic_period WHERE status = 'Active'");
        int pending = integer(conn, "SELECT count(*) FROM aspirant_profile WHERE status = 'Pending'");
        int active = integer(conn, "SELECT count(*) FROM student_profile WHERE academic_status = 'Active'");
        int groups = integer(conn, "SELECT count(*) FROM school_groups WHERE id_period = " + ACTIVE);
        int openActas = integer(conn, """
                SELECT count(*) FROM group_subject gs JOIN school_groups g ON g.id_group = gs.id_group
                WHERE g.id_period = %s AND gs.grades_closed_at IS NULL""".formatted(ACTIVE));

        List<Dashboard.CareerCount> byCareer = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement("""
                SELECT c.career_name, count(sp.id_student) FILTER (WHERE sp.academic_status = 'Active')
                FROM career c LEFT JOIN student_profile sp ON sp.id_career = c.id_career
                WHERE c.status = 'Active'
                GROUP BY c.career_name ORDER BY c.career_name""");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                byCareer.add(new Dashboard.CareerCount(rs.getString(1), rs.getInt(2)));
            }
        }

        List<Dashboard.FailingSubject> failing = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement("""
                SELECT s.subject_name, g.group_name, p.name,
                       count(*) FILTER (WHERE gr.final_grade < passing_grade()) AS failed,
                       count(*) AS graded
                FROM grades gr
                JOIN school_groups g ON g.id_group = gr.id_group
                JOIN academic_period p ON p.id_period = g.id_period
                JOIN subjects s ON s.id_subject = gr.id_subject
                WHERE gr.final_grade IS NOT NULL
                GROUP BY s.subject_name, g.group_name, p.name, p.start_date
                HAVING count(*) FILTER (WHERE gr.final_grade < passing_grade()) > 0
                ORDER BY count(*) FILTER (WHERE gr.final_grade < passing_grade())::numeric / count(*) DESC,
                         p.start_date DESC
                LIMIT ?""")) {
            ps.setInt(1, topFailing);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    failing.add(new Dashboard.FailingSubject(rs.getString(1), rs.getString(2), rs.getString(3),
                            rs.getInt(4), rs.getInt(5)));
                }
            }
        }

        List<String> missingTeacher = strings(conn, """
                SELECT g.group_name || ' · ' || s.subject_name
                FROM group_subject gs JOIN school_groups g ON g.id_group = gs.id_group
                JOIN subjects s ON s.id_subject = gs.id_subject
                WHERE g.id_period = %s AND gs.id_teacher IS NULL
                ORDER BY g.group_name, s.subject_name""".formatted(ACTIVE));

        List<String> incomplete = strings(conn, """
                SELECT g.group_name || ' · ' || s.subject_name || ' (' || count(sc.id_schedule) || ' de '
                       || s.weekly_hours || ' h)'
                FROM group_subject gs JOIN school_groups g ON g.id_group = gs.id_group
                JOIN subjects s ON s.id_subject = gs.id_subject
                LEFT JOIN schedule sc ON sc.id_group = gs.id_group AND sc.id_subject = gs.id_subject
                WHERE g.id_period = %s
                GROUP BY g.group_name, s.subject_name, s.weekly_hours
                HAVING count(sc.id_schedule) < s.weekly_hours
                ORDER BY g.group_name, s.subject_name""".formatted(ACTIVE));

        List<Dashboard.RiskStudent> risk = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement("""
                SELECT u.last_name || ', ' || u.first_name, g.group_name, s.subject_name, v.absence_pct
                FROM v_attendance_summary v
                JOIN school_groups g ON g.id_group = v.id_group
                JOIN subjects s ON s.id_subject = v.id_subject
                JOIN student_profile sp ON sp.id_student = v.id_student
                JOIN users u ON u.id_user = sp.id_user
                WHERE g.id_period = %s AND v.absence_pct > ? AND sp.academic_status = 'Active'
                ORDER BY v.absence_pct DESC, 1""".formatted(ACTIVE))) {
            ps.setBigDecimal(1, riskPct);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    risk.add(new Dashboard.RiskStudent(rs.getString(1), rs.getString(2), rs.getString(3),
                            rs.getBigDecimal(4)));
                }
            }
        }
        return new Dashboard(period, pending, active, groups, openActas, byCareer, failing, missingTeacher,
                incomplete, risk);
    }

    private static int integer(Connection conn, String sql) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    private static String string(Connection conn, String sql) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getString(1) : null;
        }
    }

    private static List<String> strings(Connection conn, String sql) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            List<String> out = new ArrayList<>();
            while (rs.next()) {
                out.add(rs.getString(1));
            }
            return out;
        }
    }
}
