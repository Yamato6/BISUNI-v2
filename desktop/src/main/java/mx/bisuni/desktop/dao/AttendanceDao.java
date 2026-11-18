package mx.bisuni.desktop.dao;

import mx.bisuni.desktop.model.AttendanceRow;
import mx.bisuni.desktop.model.AttendanceStatus;
import mx.bisuni.desktop.model.Weekday;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/** Asistencia: una marca por alumno, clase (grupo + materia) y fecha. */
public final class AttendanceDao {

    /** Alumnos del grupo con su marca de {@code date} y su resumen del periodo ({@code v_attendance_summary}). */
    public List<AttendanceRow> sheet(Connection conn, int idGroup, int idSubject, LocalDate date) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
                SELECT sp.id_student, u.last_name, u.first_name, sp.enrollment_number,
                       a.status::text AS status,
                       coalesce(v.sessions, 0) AS sessions, coalesce(v.absences, 0) AS absences, v.absence_pct
                FROM student_profile sp
                JOIN users u ON u.id_user = sp.id_user
                LEFT JOIN attendance a ON a.id_student = sp.id_student AND a.id_group = ? AND a.id_subject = ?
                                      AND a.class_date = ?
                LEFT JOIN v_attendance_summary v ON v.id_student = sp.id_student AND v.id_group = ? AND v.id_subject = ?
                WHERE sp.id_group = ?
                ORDER BY u.last_name, u.first_name, sp.id_student
                """)) {
            ps.setInt(1, idGroup);
            ps.setInt(2, idSubject);
            ps.setDate(3, Date.valueOf(date));
            ps.setInt(4, idGroup);
            ps.setInt(5, idSubject);
            ps.setInt(6, idGroup);
            List<AttendanceRow> out = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String status = rs.getString("status");
                    out.add(new AttendanceRow(rs.getInt("id_student"), rs.getString("last_name"),
                            rs.getString("first_name"), rs.getString("enrollment_number"),
                            status == null ? null : AttendanceStatus.valueOf(status),
                            rs.getInt("sessions"), rs.getInt("absences"), rs.getBigDecimal("absence_pct")));
                }
            }
            return out;
        }
    }

    /** UPSERT de la marca del alumno en esa fecha. */
    public void upsert(Connection conn, int idGroup, int idSubject, LocalDate date, int idStudent,
                       AttendanceStatus status, int recordedBy) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO attendance (id_student, id_group, id_subject, class_date, status, recorded_by)
                VALUES (?, ?, ?, ?, ?::attendance_status, ?)
                ON CONFLICT (id_student, id_group, id_subject, class_date) DO UPDATE
                SET status = EXCLUDED.status, recorded_by = EXCLUDED.recorded_by, recorded_at = now()
                """)) {
            ps.setInt(1, idStudent);
            ps.setInt(2, idGroup);
            ps.setInt(3, idSubject);
            ps.setDate(4, Date.valueOf(date));
            ps.setString(5, status.name());
            ps.setInt(6, recordedBy);
            ps.executeUpdate();
        }
    }

    /** Días de la semana en que la clase está en el horario. */
    public Set<Weekday> scheduledDays(Connection conn, int idGroup, int idSubject) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT DISTINCT day::text FROM schedule WHERE id_group = ? AND id_subject = ?")) {
            ps.setInt(1, idGroup);
            ps.setInt(2, idSubject);
            Set<Weekday> out = EnumSet.noneOf(Weekday.class);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(Weekday.valueOf(rs.getString(1)));
                }
            }
            return out;
        }
    }

    /** Fechas en que ya se pasó lista en la clase, de la más reciente a la más antigua. */
    public List<LocalDate> recordedDates(Connection conn, int idGroup, int idSubject) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
                SELECT DISTINCT class_date FROM attendance WHERE id_group = ? AND id_subject = ?
                ORDER BY class_date DESC
                """)) {
            ps.setInt(1, idGroup);
            ps.setInt(2, idSubject);
            List<LocalDate> out = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(rs.getDate(1).toLocalDate());
                }
            }
            return out;
        }
    }
}
