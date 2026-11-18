package mx.bisuni.desktop.dao;

import mx.bisuni.desktop.model.Catalog;
import mx.bisuni.desktop.model.GroupData;
import mx.bisuni.desktop.model.GroupRow;
import mx.bisuni.desktop.model.GroupSubjectRow;
import mx.bisuni.desktop.model.PeriodStatus;
import mx.bisuni.desktop.model.RecordStatus;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class GroupDao {

    /** Filtros de la lista. {@code idPeriod == null} = todos los periodos. */
    public record Filter(Integer idPeriod, Integer idCareer, Integer semester, RecordStatus status, String search) {
        public static Filter none() {
            return new Filter(null, null, null, null, null);
        }
    }

    private static final String SELECT = """
            SELECT g.id_group, g.group_name, g.id_period, p.name AS period_name, p.status::text AS period_status,
                   g.id_career, c.career_name, g.semester, g.id_tutor,
                   tu.first_name || ' ' || tu.last_name AS tutor_name, g.status::text AS status,
                   (SELECT count(*) FROM student_profile sp WHERE sp.id_group = g.id_group) AS student_count,
                   (SELECT count(*) FROM group_subject gs WHERE gs.id_group = g.id_group) AS subject_count,
                   (SELECT count(*) FROM group_subject gs
                     WHERE gs.id_group = g.id_group AND gs.id_teacher IS NULL) AS unassigned_count
            FROM school_groups g
            JOIN career c ON c.id_career = g.id_career
            JOIN academic_period p ON p.id_period = g.id_period
            LEFT JOIN teacher_profile t ON t.id_teacher = g.id_tutor
            LEFT JOIN users tu ON tu.id_user = t.id_user
            """;

    public List<GroupRow> list(Connection conn, Filter f) throws SQLException {
        String sql = SELECT + """
                WHERE (?::int IS NULL OR g.id_period = ?)
                  AND (?::int IS NULL OR g.id_career = ?)
                  AND (?::int IS NULL OR g.semester = ?)
                  AND (?::record_status IS NULL OR g.status = ?::record_status)
                  AND (?::text IS NULL OR g.group_name ILIKE ? ESCAPE '\\')
                ORDER BY p.start_date DESC, c.career_name, g.semester, g.group_name
                """;
        String pattern = Sql.containsPattern(f.search());
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            StudentDao.setNullableInt(ps, 1, f.idPeriod());
            StudentDao.setNullableInt(ps, 2, f.idPeriod());
            StudentDao.setNullableInt(ps, 3, f.idCareer());
            StudentDao.setNullableInt(ps, 4, f.idCareer());
            StudentDao.setNullableInt(ps, 5, f.semester());
            StudentDao.setNullableInt(ps, 6, f.semester());
            ps.setString(7, Sql.name(f.status()));
            ps.setString(8, Sql.name(f.status()));
            ps.setString(9, pattern);
            ps.setString(10, pattern);
            List<GroupRow> rows = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(map(rs));
                }
            }
            return rows;
        }
    }

    public Optional<GroupRow> findById(Connection conn, int idGroup) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(SELECT + " WHERE g.id_group = ?")) {
            ps.setInt(1, idGroup);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    /** Crea el grupo en {@code idPeriod}. */
    public int insert(Connection conn, GroupData d, int idPeriod) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO school_groups (group_name, id_career, semester, id_tutor, status, id_period)
                VALUES (?, ?, ?, ?, ?::record_status, ?) RETURNING id_group
                """)) {
            bind(ps, d);
            ps.setInt(6, idPeriod);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    public void update(Connection conn, int idGroup, GroupData d) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
                UPDATE school_groups SET group_name = ?, id_career = ?, semester = ?, id_tutor = ?,
                                         status = ?::record_status
                WHERE id_group = ?
                """)) {
            bind(ps, d);
            ps.setInt(6, idGroup);
            ps.executeUpdate();
        }
    }

    // ---------------- Materias del grupo ----------------

    public List<GroupSubjectRow> subjects(Connection conn, int idGroup) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
                SELECT s.id_subject, s.subject_name, s.semester, s.credits, s.weekly_hours, gs.id_teacher,
                       u.first_name || ' ' || u.last_name AS teacher_name,
                       (SELECT count(*) FROM schedule sc
                         WHERE sc.id_group = gs.id_group AND sc.id_subject = gs.id_subject) AS scheduled,
                       (SELECT count(*) FROM grades gr
                         WHERE gr.id_group = gs.id_group AND gr.id_subject = gs.id_subject) AS grade_count
                FROM group_subject gs
                JOIN subjects s ON s.id_subject = gs.id_subject
                LEFT JOIN teacher_profile t ON t.id_teacher = gs.id_teacher
                LEFT JOIN users u ON u.id_user = t.id_user
                WHERE gs.id_group = ?
                ORDER BY s.semester, s.subject_name
                """)) {
            ps.setInt(1, idGroup);
            List<GroupSubjectRow> out = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new GroupSubjectRow(
                            rs.getInt("id_subject"),
                            rs.getString("subject_name"),
                            rs.getInt("semester"),
                            rs.getInt("credits"),
                            rs.getInt("weekly_hours"),
                            rs.getObject("id_teacher", Integer.class),
                            rs.getString("teacher_name"),
                            rs.getInt("scheduled"),
                            rs.getInt("grade_count")));
                }
            }
            return out;
        }
    }

    /** Profesores activos habilitados para la materia ({@code teacher_subject}). */
    public List<Catalog.Teacher> eligibleTeachers(Connection conn, int idSubject) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
                SELECT t.id_teacher, u.first_name || ' ' || u.last_name
                FROM teacher_subject ts
                JOIN teacher_profile t ON t.id_teacher = ts.id_teacher
                JOIN users u ON u.id_user = t.id_user
                WHERE ts.id_subject = ? AND u.status = 'Active'
                ORDER BY u.last_name, u.first_name
                """)) {
            ps.setInt(1, idSubject);
            List<Catalog.Teacher> out = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new Catalog.Teacher(rs.getInt(1), rs.getString(2)));
                }
            }
            return out;
        }
    }

    public void addSubject(Connection conn, int idGroup, int idSubject) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO group_subject (id_group, id_subject) VALUES (?, ?)")) {
            ps.setInt(1, idGroup);
            ps.setInt(2, idSubject);
            ps.executeUpdate();
        }
    }

    /** Borra la materia del grupo; su horario se borra en cascada. Devuelve {@code false} si no existía. */
    public boolean removeSubject(Connection conn, int idGroup, int idSubject) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "DELETE FROM group_subject WHERE id_group = ? AND id_subject = ?")) {
            ps.setInt(1, idGroup);
            ps.setInt(2, idSubject);
            return ps.executeUpdate() == 1;
        }
    }

    /** Cambia el profesor; el horario de la materia se actualiza en cascada (FK ON UPDATE CASCADE). */
    public boolean setTeacher(Connection conn, int idGroup, int idSubject, Integer idTeacher) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE group_subject SET id_teacher = ? WHERE id_group = ? AND id_subject = ?")) {
            StudentDao.setNullableInt(ps, 1, idTeacher);
            ps.setInt(2, idGroup);
            ps.setInt(3, idSubject);
            return ps.executeUpdate() == 1;
        }
    }

    public int scheduledHours(Connection conn, int idGroup, int idSubject) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT count(*) FROM schedule WHERE id_group = ? AND id_subject = ?")) {
            ps.setInt(1, idGroup);
            ps.setInt(2, idSubject);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    // ---------------- Alumnos del grupo ----------------

    /** Asigna el alumno al grupo. La FK compuesta rechaza grupos de otra carrera. */
    public boolean setStudentGroup(Connection conn, int idStudent, Integer idGroup) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE student_profile SET id_group = ? WHERE id_student = ?")) {
            StudentDao.setNullableInt(ps, 1, idGroup);
            ps.setInt(2, idStudent);
            return ps.executeUpdate() == 1;
        }
    }

    // ---------------- Reinscripción y egreso ----------------

    public int careerTotalSemesters(Connection conn, int idCareer) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT total_semesters FROM career WHERE id_career = ?")) {
            ps.setInt(1, idCareer);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    /** Agrega al grupo las materias activas de la carrera para ese semestre (sin profesor). */
    public int addSubjectsOfSemester(Connection conn, int idGroup, int idCareer, int semester) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO group_subject (id_group, id_subject)
                SELECT ?, id_subject FROM subjects WHERE id_career = ? AND semester = ? AND status = 'Active'
                """)) {
            ps.setInt(1, idGroup);
            ps.setInt(2, idCareer);
            ps.setInt(3, semester);
            return ps.executeUpdate();
        }
    }

    /** Pasa a los alumnos activos de un grupo a otro y les asigna el semestre. */
    public int moveActiveStudents(Connection conn, int fromGroup, int toGroup, int semester) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
                UPDATE student_profile SET id_group = ?, semester = ?
                WHERE id_group = ? AND academic_status = 'Active'
                """)) {
            ps.setInt(1, toGroup);
            ps.setInt(2, semester);
            ps.setInt(3, fromGroup);
            return ps.executeUpdate();
        }
    }

    /**
     * Egresa a los alumnos activos del grupo que no deben materias (ninguna reprobada sin una
     * aprobación posterior de la misma materia). Quedan sin grupo y con estatus Graduated.
     */
    public int graduateStudents(Connection conn, int idGroup) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
                UPDATE student_profile sp SET academic_status = 'Graduated', id_group = NULL
                WHERE sp.id_group = ? AND sp.academic_status = 'Active'
                  AND NOT EXISTS (
                      SELECT 1 FROM v_kardex k
                      WHERE k.id_student = sp.id_student AND k.result = 'Failed'
                        AND NOT EXISTS (SELECT 1 FROM v_kardex k2
                                        WHERE k2.id_student = k.id_student AND k2.id_subject = k.id_subject
                                          AND k2.result = 'Passed'))
                """)) {
            ps.setInt(1, idGroup);
            return ps.executeUpdate();
        }
    }

    /** Nombres ("Apellidos, Nombre") de los alumnos activos que siguen en el grupo. */
    public List<String> activeStudentNames(Connection conn, int idGroup) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
                SELECT u.last_name || ', ' || u.first_name
                FROM student_profile sp JOIN users u ON u.id_user = sp.id_user
                WHERE sp.id_group = ? AND sp.academic_status = 'Active'
                ORDER BY u.last_name, u.first_name
                """)) {
            ps.setInt(1, idGroup);
            List<String> out = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(rs.getString(1));
                }
            }
            return out;
        }
    }

    private static void bind(PreparedStatement ps, GroupData d) throws SQLException {
        ps.setString(1, d.name());
        ps.setInt(2, d.idCareer());
        ps.setInt(3, d.semester());
        StudentDao.setNullableInt(ps, 4, d.idTutor());
        ps.setString(5, d.status().name());
    }

    private static GroupRow map(ResultSet rs) throws SQLException {
        return new GroupRow(
                rs.getInt("id_group"),
                rs.getString("group_name"),
                rs.getInt("id_period"),
                rs.getString("period_name"),
                PeriodStatus.valueOf(rs.getString("period_status")),
                rs.getInt("id_career"),
                rs.getString("career_name"),
                rs.getInt("semester"),
                rs.getObject("id_tutor", Integer.class),
                rs.getString("tutor_name"),
                RecordStatus.valueOf(rs.getString("status")),
                rs.getInt("student_count"),
                rs.getInt("subject_count"),
                rs.getInt("unassigned_count"));
    }
}
