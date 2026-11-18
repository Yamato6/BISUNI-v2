package mx.bisuni.desktop.dao;

import mx.bisuni.desktop.model.AuditRow;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/** Consulta de la bitácora. Los renglones los escriben triggers; aquí solo se leen. */
public final class AuditDao {

    /** Filtros: fechas inclusivas, entidad exacta y texto en el actor o en el registro afectado. */
    public record Filter(LocalDate from, LocalDate to, String entity, String search, int limit) {
    }

    /*
     * "subject" traduce entity_id a algo legible. Formatos de entity_id (ver V1__schema.sql):
     * users/career/academic_period/aspirant_profile/student_profile = id; group_subject = grupo:materia;
     * grades = alumno:grupo:materia.
     */
    private static final String SELECT = """
            SELECT l.id_audit, l.at, au.first_name || ' ' || au.last_name AS actor, l.action, l.entity, l.entity_id,
                   (SELECT string_agg(e.key || '|'
                               || CASE jsonb_typeof(e.value) WHEN 'object'
                                       THEN coalesce(e.value ->> 'antes', '') || '|' || coalesce(e.value ->> 'despues', '')
                                       ELSE '|' || (e.value #>> '{}') END,
                               chr(10) ORDER BY e.key)
                    FROM jsonb_each(l.details) e) AS details,
                   CASE l.entity
                     WHEN 'users' THEN (SELECT first_name || ' ' || last_name || ' (' || email || ')'
                                        FROM users WHERE id_user = split_part(l.entity_id, ':', 1)::int)
                     WHEN 'career' THEN (SELECT career_name FROM career WHERE id_career = l.entity_id::int)
                     WHEN 'academic_period' THEN (SELECT name FROM academic_period WHERE id_period = l.entity_id::int)
                     WHEN 'aspirant_profile' THEN (SELECT 'ASP-' || lpad(a.id_aspirant::text, 6, '0') || ' ' || u.first_name || ' ' || u.last_name
                                                   FROM aspirant_profile a JOIN users u ON u.id_user = a.id_user
                                                   WHERE a.id_aspirant = l.entity_id::int)
                     WHEN 'student_profile' THEN (SELECT s.enrollment_number || ' ' || u.first_name || ' ' || u.last_name
                                                  FROM student_profile s JOIN users u ON u.id_user = s.id_user
                                                  WHERE s.id_student = l.entity_id::int)
                     WHEN 'group_subject' THEN (SELECT g.group_name || ' (' || p.name || ') · ' || sub.subject_name
                                                FROM school_groups g JOIN academic_period p ON p.id_period = g.id_period,
                                                     subjects sub
                                                WHERE g.id_group = split_part(l.entity_id, ':', 1)::int
                                                  AND sub.id_subject = split_part(l.entity_id, ':', 2)::int)
                     WHEN 'grades' THEN (SELECT u.first_name || ' ' || u.last_name || ' · ' || g.group_name || ' · ' || sub.subject_name
                                         FROM student_profile s JOIN users u ON u.id_user = s.id_user,
                                              school_groups g, subjects sub
                                         WHERE s.id_student = split_part(l.entity_id, ':', 1)::int
                                           AND g.id_group = split_part(l.entity_id, ':', 2)::int
                                           AND sub.id_subject = split_part(l.entity_id, ':', 3)::int)
                   END AS subject
            FROM audit_log l
            LEFT JOIN users au ON au.id_user = l.actor_user
            """;

    public List<AuditRow> list(Connection conn, Filter f) throws SQLException {
        String sql = "SELECT * FROM (" + SELECT + """
                WHERE (?::date IS NULL OR l.at >= ?::date)
                  AND (?::date IS NULL OR l.at < ?::date + 1)
                  AND (?::text IS NULL OR l.entity = ?)
                ) x
                WHERE (?::text IS NULL OR x.actor ILIKE ? ESCAPE '\\' OR x.subject ILIKE ? ESCAPE '\\')
                ORDER BY x.at DESC, x.id_audit DESC
                LIMIT ?
                """;
        String pattern = Sql.containsPattern(f.search());
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            setDate(ps, 1, f.from());
            setDate(ps, 2, f.from());
            setDate(ps, 3, f.to());
            setDate(ps, 4, f.to());
            ps.setString(5, f.entity());
            ps.setString(6, f.entity());
            ps.setString(7, pattern);
            ps.setString(8, pattern);
            ps.setString(9, pattern);
            ps.setInt(10, f.limit());
            List<AuditRow> out = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new AuditRow(rs.getLong("id_audit"), rs.getObject("at", OffsetDateTime.class),
                            rs.getString("actor"), rs.getString("action"), rs.getString("entity"),
                            rs.getString("entity_id"), rs.getString("subject"), rs.getString("details")));
                }
            }
            return out;
        }
    }

    private static void setDate(PreparedStatement ps, int i, LocalDate d) throws SQLException {
        if (d == null) {
            ps.setNull(i, Types.DATE);
        } else {
            ps.setDate(i, Date.valueOf(d));
        }
    }
}
