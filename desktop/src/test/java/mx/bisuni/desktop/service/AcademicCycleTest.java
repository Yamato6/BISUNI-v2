package mx.bisuni.desktop.service;

import mx.bisuni.desktop.dao.GroupDao;
import mx.bisuni.desktop.dao.PeriodDao;
import mx.bisuni.desktop.dao.StudentDao;
import mx.bisuni.desktop.dao.SubjectDao;
import mx.bisuni.desktop.dao.UserDao;
import mx.bisuni.desktop.db.Database;
import mx.bisuni.desktop.model.Role;
import mx.bisuni.desktop.model.StudentStatus;
import mx.bisuni.desktop.session.UserSession;
import mx.bisuni.desktop.support.PostgresTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static mx.bisuni.desktop.support.Sql.count;
import static mx.bisuni.desktop.support.Sql.exec;
import static mx.bisuni.desktop.support.Sql.intOrNull;
import static mx.bisuni.desktop.support.Sql.text;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Cierre de ciclo: reinscripción, egreso y situación académica del alumno.
 * Seed: IT-1A (grupo 1, 2026-2, 1er semestre, alumnos 1-10); IT-2A (grupo 7, 2026-2, 2° semestre,
 * alumnos 53-61); el 55 y el 58 reprobaron Discrete Mathematics en 2026-1 y el 62 se dio de baja.
 */
class AcademicCycleTest {

    private static final UserSession ADMIN =
            new UserSession(1, "admin.master@bisuni.edu", "Antonio", "Ramírez", Role.Admin, 1);

    private Database db;
    private GroupService groups;
    private StudentService students;

    @BeforeEach
    void setUp() {
        db = PostgresTestSupport.freshDatabase();
        groups = new GroupService(db, new GroupDao(), new SubjectDao(), new StudentDao(), new PeriodDao());
        students = new StudentService(db, new StudentDao(), new UserDao());
    }

    /** Cierra 2026-2 (con sus actas) y activa 2027-1. Devuelve el id del nuevo periodo. */
    private int closeCurrentAndOpenNext() throws Exception {
        exec(db, "UPDATE group_subject SET grades_closed_at = now(), grades_closed_by = 3 "
                + "WHERE id_group IN (SELECT id_group FROM school_groups WHERE id_period = 2)");
        exec(db, "UPDATE academic_period SET status = 'Closed', admissions_open = false WHERE id_period = 2");
        exec(db, "INSERT INTO academic_period (name, start_date, end_date, status) "
                + "VALUES ('2027-1', '2027-01-11', '2027-06-25', 'Active')");
        return count(db, "SELECT id_period FROM academic_period WHERE name = '2027-1'");
    }

    // ---------------- Reinscripción ----------------

    @Test
    void promotionNeedsAClosedPeriod() {
        var e = assertThrows(ServiceException.class, () -> groups.promote(1, 2, "IT-2B", ADMIN));
        assertTrue(e.getMessage().contains("Solo se reinscriben grupos de un periodo cerrado"), e.getMessage());
    }

    @Test
    void promoteMovesActiveStudentsToTheNextSemester() throws Exception {
        int next = closeCurrentAndOpenNext();
        int gradesBefore = count(db, "SELECT count(*) FROM grades WHERE id_group = 1");

        var r = groups.promote(1, next, "it-2a", ADMIN);

        assertEquals("IT-2A", r.groupName());
        assertEquals("2027-1", r.periodName());
        assertEquals(10, r.students());
        assertEquals(2, r.subjects(), "Database Basics y Web Development I (2° semestre de IT)");
        assertEquals("1|2|" + next + "|1", text(db, """
                SELECT id_career || '|' || semester || '|' || id_period || '|' || id_tutor
                FROM school_groups WHERE id_group = ?""", r.idGroup()));
        assertEquals(10, count(db, "SELECT count(*) FROM student_profile WHERE id_group = ? AND semester = 2",
                r.idGroup()));
        assertEquals(0, count(db, "SELECT count(*) FROM student_profile WHERE id_group = 1"));
        assertEquals(gradesBefore, count(db, "SELECT count(*) FROM grades WHERE id_group = 1"),
                "las calificaciones se quedan en el grupo donde se cursaron");
        assertEquals(0, count(db, "SELECT count(*) FROM group_subject WHERE id_group = ? AND id_teacher IS NOT NULL",
                r.idGroup()), "las materias nuevas llegan sin profesor");
        assertEquals(10, count(db, "SELECT count(*) FROM audit_log WHERE entity = 'student_profile' "
                + "AND actor_user = 1 AND details -> 'id_group' IS NOT NULL"));

        // Los grupos de 2026-1 ya no tienen alumnos activos que reinscribir.
        var empty = assertThrows(ServiceException.class, () -> groups.promote(6, next, "IT-2Z", ADMIN));
        assertTrue(empty.getMessage().contains("no tiene alumnos activos"), empty.getMessage());
    }

    // ---------------- Egreso ----------------

    @Test
    void graduationOnlyForStudentsWhoOweNothing() throws Exception {
        // IT-2A termina sus dos materias con 8 y la carrera se acorta a 2 semestres para el escenario.
        exec(db, "UPDATE grades SET parcial1 = 8, parcial2 = 8, parcial3 = 8 WHERE id_group = 7 AND id_subject = 3");
        exec(db, "INSERT INTO grades (id_student, id_group, id_subject, parcial1, parcial2, parcial3) "
                + "SELECT i, 7, 4, 8, 8, 8 FROM generate_series(53, 61) AS i");
        exec(db, "UPDATE career SET total_semesters = 2 WHERE id_career = 1");
        int next = closeCurrentAndOpenNext();

        var e = assertThrows(ServiceException.class, () -> groups.promote(7, next, "IT-3A", ADMIN));
        assertTrue(e.getMessage().contains("egresan, no se reinscriben"), e.getMessage());
        var notLast = assertThrows(ServiceException.class, () -> groups.graduate(1, ADMIN));
        assertTrue(notLast.getMessage().contains("se reinscribe al siguiente"), notLast.getMessage());

        var r = groups.graduate(7, ADMIN);
        assertEquals(7, r.graduated());
        assertEquals(List.of("Duarte, Paulina", "Fuentes, Rodrigo"), r.pending(),
                "los dos deben Discrete Mathematics de 2026-1");
        assertEquals(7, count(db, "SELECT count(*) FROM student_profile WHERE academic_status = 'Graduated' "
                + "AND id_group IS NULL AND id_student BETWEEN 53 AND 61"));
        assertEquals(7, count(db, "SELECT id_group FROM student_profile WHERE id_student = 58"));
    }

    // ---------------- Situación académica ----------------

    @Test
    void leaveTakesTheStudentOutOfTheGroupAndKeepsGrades() throws Exception {
        int grades = count(db, "SELECT count(*) FROM grades WHERE id_student = 1");
        students.changeAcademicStatus(1, StudentStatus.Leave, ADMIN);
        assertEquals("Leave", text(db, "SELECT academic_status::text FROM student_profile WHERE id_student = 1"));
        assertNull(intOrNull(db, "SELECT id_group FROM student_profile WHERE id_student = 1"));
        assertEquals(grades, count(db, "SELECT count(*) FROM grades WHERE id_student = 1"));

        students.changeAcademicStatus(1, StudentStatus.Active, ADMIN);
        assertEquals("Active", text(db, "SELECT academic_status::text FROM student_profile WHERE id_student = 1"));
    }

    @Test
    void transitionsAndDebts() throws Exception {
        var owes = assertThrows(ServiceException.class,
                () -> students.changeAcademicStatus(58, StudentStatus.Graduated, ADMIN));
        assertEquals("No puede egresar: debe Discrete Mathematics.", owes.getMessage());

        var bad = assertThrows(ServiceException.class,
                () -> students.changeAcademicStatus(62, StudentStatus.Leave, ADMIN));
        assertTrue(bad.getMessage().contains("baja definitiva a baja temporal"), bad.getMessage());
        students.changeAcademicStatus(62, StudentStatus.Active, ADMIN); // reingreso

        students.changeAcademicStatus(53, StudentStatus.Graduated, ADMIN);
        var fin = assertThrows(ServiceException.class,
                () -> students.changeAcademicStatus(53, StudentStatus.Active, ADMIN));
        assertEquals("El egreso es definitivo.", fin.getMessage());
    }

    @Test
    void inactiveStudentsCannotJoinAGroup() throws Exception {
        var e = assertThrows(ServiceException.class, () -> groups.addStudent(7, 62, ADMIN));
        assertTrue(e.getMessage().contains("reactívalo"), e.getMessage());
    }
}
