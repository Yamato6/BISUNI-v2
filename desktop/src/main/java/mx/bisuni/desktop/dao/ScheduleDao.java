package mx.bisuni.desktop.dao;

import mx.bisuni.desktop.model.ScheduleEntry;
import mx.bisuni.desktop.model.TimeSlot;
import mx.bisuni.desktop.model.Weekday;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Horario: una sola tabla {@code schedule}; el del profesor sale de la vista {@code v_teacher_schedule}. */
public final class ScheduleDao {

    private static final String SELECT = """
            SELECT s.day::text AS day, s.id_slot, s.id_group, g.group_name, s.id_subject, sub.subject_name,
                   s.id_teacher, u.first_name || ' ' || u.last_name AS teacher_name
            FROM schedule s
            JOIN school_groups   g   ON g.id_group     = s.id_group
            JOIN subjects        sub ON sub.id_subject = s.id_subject
            JOIN teacher_profile t   ON t.id_teacher   = s.id_teacher
            JOIN users           u   ON u.id_user      = t.id_user
            """;

    public List<TimeSlot> timeSlots(Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id_slot, slot_number, start_time, end_time FROM time_slots ORDER BY slot_number");
             ResultSet rs = ps.executeQuery()) {
            List<TimeSlot> out = new ArrayList<>();
            while (rs.next()) {
                out.add(new TimeSlot(rs.getInt(1), rs.getInt(2),
                        rs.getObject(3, LocalTime.class), rs.getObject(4, LocalTime.class)));
            }
            return out;
        }
    }

    public List<ScheduleEntry> forGroup(Connection conn, int idGroup) throws SQLException {
        return query(conn, SELECT + " WHERE s.id_group = ?", idGroup);
    }

    /** Horario del profesor a partir de la vista {@code v_teacher_schedule}. */
    public List<ScheduleEntry> forTeacher(Connection conn, int idTeacher) throws SQLException {
        return query(conn, """
                SELECT v.day::text AS day, v.id_slot, v.id_group, v.group_name, v.id_subject, v.subject_name,
                       v.id_teacher, u.first_name || ' ' || u.last_name AS teacher_name
                FROM v_teacher_schedule v
                JOIN teacher_profile t ON t.id_teacher = v.id_teacher
                JOIN users u ON u.id_user = t.id_user
                WHERE v.id_teacher = ?
                """, idTeacher);
    }

    /** Clase que el profesor ya tiene en ese día y bloque, en un grupo distinto de {@code exceptGroup}. */
    public Optional<ScheduleEntry> teacherBusy(Connection conn, int idTeacher, Weekday day, int idSlot,
                                               int exceptGroup) throws SQLException {
        List<ScheduleEntry> found = query(conn, SELECT
                        + " WHERE s.id_teacher = ? AND s.day = ?::weekday AND s.id_slot = ? AND s.id_group <> ?",
                idTeacher, day.name(), idSlot, exceptGroup);
        return found.stream().findFirst();
    }

    public Optional<ScheduleEntry> at(Connection conn, int idGroup, Weekday day, int idSlot) throws SQLException {
        return query(conn, SELECT + " WHERE s.id_group = ? AND s.day = ?::weekday AND s.id_slot = ?",
                idGroup, day.name(), idSlot).stream().findFirst();
    }

    /** Inserta la clase tomando el profesor de {@code group_subject}. Devuelve {@code false} si no hay profesor. */
    public boolean insert(Connection conn, int idGroup, Weekday day, int idSlot, int idSubject) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO schedule (day, id_slot, id_group, id_subject, id_teacher)
                SELECT ?::weekday, ?, gs.id_group, gs.id_subject, gs.id_teacher
                FROM group_subject gs
                WHERE gs.id_group = ? AND gs.id_subject = ? AND gs.id_teacher IS NOT NULL
                """)) {
            ps.setString(1, day.name());
            ps.setInt(2, idSlot);
            ps.setInt(3, idGroup);
            ps.setInt(4, idSubject);
            return ps.executeUpdate() == 1;
        }
    }

    public boolean delete(Connection conn, int idGroup, Weekday day, int idSlot) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "DELETE FROM schedule WHERE id_group = ? AND day = ?::weekday AND id_slot = ?")) {
            ps.setInt(1, idGroup);
            ps.setString(2, day.name());
            ps.setInt(3, idSlot);
            return ps.executeUpdate() == 1;
        }
    }

    private static List<ScheduleEntry> query(Connection conn, String sql, Object... params) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql + " ORDER BY 2, 1")) {
            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 1, params[i]);
            }
            List<ScheduleEntry> out = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new ScheduleEntry(
                            Weekday.valueOf(rs.getString("day")),
                            rs.getInt("id_slot"),
                            rs.getInt("id_group"),
                            rs.getString("group_name"),
                            rs.getInt("id_subject"),
                            rs.getString("subject_name"),
                            rs.getInt("id_teacher"),
                            rs.getString("teacher_name")));
                }
            }
            return out;
        }
    }
}
