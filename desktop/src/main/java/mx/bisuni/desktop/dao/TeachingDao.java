package mx.bisuni.desktop.dao;

import mx.bisuni.desktop.model.TeacherClass;
import mx.bisuni.desktop.model.TeacherGroup;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Consultas desde el punto de vista del profesor. Todo sale de {@code group_subject.id_teacher}
 * (quién imparte realmente cada clase), no de {@code teacher_subject} (quién podría impartirla),
 * que es lo que usaba la v1 y mostraba grupos ajenos.
 */
public final class TeachingDao {

    /** Clases (grupo + materia) que imparte el profesor en el periodo activo. */
    public List<TeacherClass> classes(Connection conn, int idTeacher) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
                SELECT gs.id_group, g.group_name, c.career_name, g.semester, gs.id_subject, s.subject_name,
                       s.weekly_hours,
                       (SELECT count(*) FROM student_profile sp WHERE sp.id_group = gs.id_group) AS student_count,
                       (SELECT count(*) FROM schedule sc
                         WHERE sc.id_group = gs.id_group AND sc.id_subject = gs.id_subject) AS scheduled,
                       (SELECT count(*) FROM grades gr
                         WHERE gr.id_group = gs.id_group AND gr.id_subject = gs.id_subject
                           AND gr.final_grade IS NOT NULL) AS graded
                FROM group_subject gs
                JOIN school_groups g ON g.id_group   = gs.id_group
                JOIN career        c ON c.id_career  = g.id_career
                JOIN subjects      s ON s.id_subject = gs.id_subject
                JOIN academic_period p ON p.id_period = g.id_period AND p.status = 'Active'
                WHERE gs.id_teacher = ?
                ORDER BY g.group_name, s.subject_name
                """)) {
            ps.setInt(1, idTeacher);
            List<TeacherClass> out = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new TeacherClass(
                            rs.getInt("id_group"), rs.getString("group_name"), rs.getString("career_name"),
                            rs.getInt("semester"), rs.getInt("id_subject"), rs.getString("subject_name"),
                            rs.getInt("weekly_hours"), rs.getInt("student_count"), rs.getInt("scheduled"),
                            rs.getInt("graded")));
                }
            }
            return out;
        }
    }

    /** Grupos del periodo activo donde el profesor imparte alguna materia o es tutor. */
    public List<TeacherGroup> groups(Connection conn, int idTeacher) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
                SELECT g.id_group, g.group_name, c.career_name, g.semester,
                       coalesce(g.id_tutor = ?, false) AS is_tutor,
                       coalesce((SELECT string_agg(s.subject_name, ', ' ORDER BY s.subject_name)
                                 FROM group_subject gs JOIN subjects s ON s.id_subject = gs.id_subject
                                 WHERE gs.id_group = g.id_group AND gs.id_teacher = ?), '') AS my_subjects,
                       (SELECT count(*) FROM student_profile sp WHERE sp.id_group = g.id_group) AS student_count
                FROM school_groups g
                JOIN career c ON c.id_career = g.id_career
                JOIN academic_period p ON p.id_period = g.id_period AND p.status = 'Active'
                WHERE (g.id_tutor = ?
                       OR EXISTS (SELECT 1 FROM group_subject gs WHERE gs.id_group = g.id_group AND gs.id_teacher = ?))
                ORDER BY g.group_name
                """)) {
            for (int i = 1; i <= 4; i++) {
                ps.setInt(i, idTeacher);
            }
            List<TeacherGroup> out = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new TeacherGroup(rs.getInt("id_group"), rs.getString("group_name"),
                            rs.getString("career_name"), rs.getInt("semester"), rs.getBoolean("is_tutor"),
                            rs.getString("my_subjects"), rs.getInt("student_count")));
                }
            }
            return out;
        }
    }

    /** ¿El profesor imparte {@code idSubject} en {@code idGroup}? */
    public boolean teaches(Connection conn, int idTeacher, int idGroup, int idSubject) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT 1 FROM group_subject WHERE id_teacher = ? AND id_group = ? AND id_subject = ?")) {
            ps.setInt(1, idTeacher);
            ps.setInt(2, idGroup);
            ps.setInt(3, idSubject);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }
}
