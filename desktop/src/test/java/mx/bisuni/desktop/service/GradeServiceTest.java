package mx.bisuni.desktop.service;

import mx.bisuni.desktop.dao.GradeDao;
import mx.bisuni.desktop.dao.StudentDao;
import mx.bisuni.desktop.dao.TeachingDao;
import mx.bisuni.desktop.db.Database;
import mx.bisuni.desktop.model.GradeInput;
import mx.bisuni.desktop.model.GradeRow;
import mx.bisuni.desktop.model.Role;
import mx.bisuni.desktop.model.TeacherClass;
import mx.bisuni.desktop.model.TeacherGroup;
import mx.bisuni.desktop.session.UserSession;
import mx.bisuni.desktop.support.PostgresTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static mx.bisuni.desktop.support.Sql.count;
import static mx.bisuni.desktop.support.Sql.exec;
import static mx.bisuni.desktop.support.Sql.text;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Seed: Juan Pérez (profesor 1) da la materia 1 en IT-1A (grupo 1) e IT-1B (grupo 2) y es tutor de
 * IT-1A. María García (profesor 4) da la materia 2 en ambos grupos. Las calificaciones del seed
 * son de IT-1A, materias 1 y 2, alumnos 1 a 10.
 */
class GradeServiceTest {

    private static final UserSession JUAN =
            new UserSession(3, "juan.perez@bisuni.edu", "Juan", "Pérez", Role.Teacher, 1);
    private static final UserSession MARIA =
            new UserSession(6, "maria.garcia@bisuni.edu", "María", "García", Role.Teacher, 4);
    private static final UserSession ADMIN =
            new UserSession(1, "admin.master@bisuni.edu", "Antonio", "Ramírez", Role.Admin, 1);

    private Database db;
    private GradeService grades;
    private TeachingService teaching;

    @BeforeEach
    void setUp() {
        db = PostgresTestSupport.freshDatabase();
        grades = new GradeService(db, new GradeDao(), new TeachingDao());
        teaching = new TeachingService(db, new TeachingDao(), new StudentDao());
    }

    /** En el seed todos los alumnos de IT-1A ya tienen calificaciones; se mete a uno de IT sin grupo. */
    private int newcomerInGroup1() throws Exception {
        int student = count(db, "SELECT min(id_student) FROM student_profile WHERE id_career = 1 AND id_group IS NULL");
        exec(db, "UPDATE student_profile SET id_group = 1 WHERE id_student = " + student);
        return student;
    }

    private static BigDecimal d(String v) {
        return v == null ? null : new BigDecimal(v);
    }

    // ---------------- Clases y grupos del profesor ----------------

    @Test
    void myClassesComeFromGroupSubjectTeacher() throws Exception {
        List<TeacherClass> classes = teaching.myClasses(JUAN);
        assertEquals(2, classes.size(), "materia 1 en IT-1A e IT-1B");
        assertTrue(classes.stream().allMatch(c -> c.idSubject() == 1));
        TeacherClass it1a = classes.stream().filter(c -> c.idGroup() == 1).findFirst().orElseThrow();
        assertEquals(5, it1a.scheduledHours());
        assertEquals(10, it1a.gradedCount());

        // Habilitar a Juan para otra materia NO le da grupos nuevos (la v1 sí los mostraba).
        exec(db, "INSERT INTO teacher_subject (id_teacher, id_subject) VALUES (1, 2)");
        assertEquals(2, teaching.myClasses(JUAN).size());
    }

    @Test
    void myGroupsIncludeTutorship() throws Exception {
        List<TeacherGroup> groups = teaching.myGroups(JUAN);
        TeacherGroup it1a = groups.stream().filter(g -> g.idGroup() == 1).findFirst().orElseThrow();
        assertTrue(it1a.tutor());
        assertEquals("Programming Fundamentals", it1a.mySubjects());

        // Tutor de un grupo donde no da clase: aparece con "mis materias" vacío.
        exec(db, "UPDATE school_groups SET id_tutor = 1 WHERE id_group = 3");
        TeacherGroup ba = teaching.myGroups(JUAN).stream().filter(g -> g.idGroup() == 3).findFirst().orElseThrow();
        assertTrue(ba.tutor());
        assertEquals("", ba.mySubjects());
    }

    @Test
    void groupStudentsOnlyForRelatedGroups() throws Exception {
        assertFalse(teaching.groupStudents(1, JUAN).isEmpty());
        var e = assertThrows(ServiceException.class, () -> teaching.groupStudents(3, JUAN));
        assertTrue(e.getMessage().contains("No impartes clases en ese grupo"), e.getMessage());
    }

    @Test
    void adminsUseTheirOwnScreens() {
        assertThrows(ServiceException.class, () -> teaching.myClasses(ADMIN));
        assertThrows(ServiceException.class, () -> grades.sheet(1, 1, ADMIN));
    }

    // ---------------- Hoja de calificaciones ----------------

    @Test
    void sheetShowsCurrentStudentsWithGrades() throws Exception {
        List<GradeRow> sheet = grades.sheet(1, 1, JUAN);
        assertEquals(count(db, "SELECT count(*) FROM student_profile WHERE id_group = 1"), sheet.size());
        GradeRow s1 = sheet.stream().filter(r -> r.idStudent() == 1).findFirst().orElseThrow();
        assertEquals(0, s1.finalGrade().compareTo(d("8.17")), "promedio de 8.0, 8.0 y 8.5");
    }

    @Test
    void savingIsAuditedWithTheTeacher() throws Exception {
        grades.save(1, 1, List.of(new GradeInput(2, d("10"), d("9"), d("9"))), JUAN);
        assertEquals("{\"antes\": 8.50, \"despues\": 10.00}", text(db, """
                SELECT details -> 'parcial1' FROM audit_log
                WHERE entity = 'grades' AND entity_id = '2:1:1' AND actor_user = 3 AND action = 'update'"""));
    }

    @Test
    void closedActaAndClosedPeriodAreReadOnly() throws Exception {
        // Acta cerrada: la base rechaza el cambio (trigger grades_guard).
        exec(db, "UPDATE group_subject SET grades_closed_at = now(), grades_closed_by = 3 WHERE id_group = 1 AND id_subject = 1");
        var e = assertThrows(ServiceException.class, () -> grades.save(1, 1,
                List.of(new GradeInput(1, d("10"), d("10"), d("10"))), JUAN));
        assertTrue(e.getMessage().contains("acta de esta materia ya está cerrada"), e.getMessage());
        // Periodo cerrado: Juan dio la materia 1 en el IT-1A de 2026-1 (grupo 6), ya no aparece en sus clases.
        assertTrue(teaching.myClasses(JUAN).stream().noneMatch(c -> c.idGroup() == 6));
    }

    @Test
    void onlyTheAssignedTeacherSeesTheSheet() {
        var e = assertThrows(ServiceException.class, () -> grades.sheet(1, 1, MARIA));
        assertEquals("No impartes esa materia en ese grupo.", e.getMessage());
        assertThrows(ServiceException.class, () -> grades.save(1, 1,
                List.of(new GradeInput(1, d("10"), d("10"), d("10"))), MARIA));
    }

    @Test
    void saveUpdatesExistingAndFinalIsGeneratedByTheDatabase() throws Exception {
        int rowsBefore = count(db, "SELECT count(*) FROM grades");
        int saved = grades.save(1, 1, List.of(new GradeInput(1, d("9.5"), d("7"), d("8.25"))), JUAN);
        assertEquals(1, saved);
        assertEquals(rowsBefore, count(db, "SELECT count(*) FROM grades"), "UPSERT: no duplica");
        assertEquals("9.50|7.00|8.25|8.25", text(db, """
                SELECT parcial1 || '|' || parcial2 || '|' || parcial3 || '|' || final_grade
                FROM grades WHERE id_student = 1 AND id_group = 1 AND id_subject = 1"""));
        assertEquals(0, GradeService.finalGrade(d("9.5"), d("7"), d("8.25")).compareTo(d("8.25")));
    }

    @Test
    void saveInsertsNewRowAndPartialGradesHaveNoFinal() throws Exception {
        // Alumno del grupo 1 sin calificaciones de la materia 1.
        int student = newcomerInGroup1();
        grades.save(1, 1, List.of(new GradeInput(student, d("8"), null, null)), JUAN);
        GradeRow row = grades.sheet(1, 1, JUAN).stream().filter(r -> r.idStudent() == student).findFirst().orElseThrow();
        assertEquals(0, row.parcial1().compareTo(d("8")));
        assertNull(row.finalGrade(), "sin los 3 parciales no hay final");
    }

    @Test
    void emptyRowWithoutPreviousGradesIsSkipped() throws Exception {
        int student = newcomerInGroup1();
        assertEquals(0, grades.save(1, 1, List.of(new GradeInput(student, null, null, null)), JUAN));
        assertEquals(0, count(db, "SELECT count(*) FROM grades WHERE id_student = ? AND id_group = 1 AND id_subject = 1",
                student));
    }

    @Test
    void studentOutsideTheClassIsRejectedAndNothingIsSaved() throws Exception {
        int outsider = count(db, "SELECT min(id_student) FROM student_profile WHERE id_career <> 1");
        var e = assertThrows(ServiceException.class, () -> grades.save(1, 1, List.of(
                new GradeInput(1, d("10"), d("10"), d("10")),
                new GradeInput(outsider, d("9"), d("9"), d("9"))), JUAN));
        assertTrue(e.getMessage().contains("ya no pertenece a esta clase"), e.getMessage());
        assertEquals("8.00", text(db, "SELECT parcial1::text FROM grades WHERE id_student = 1 AND id_group = 1 AND id_subject = 1"),
                "rollback: el alumno 1 conserva su calificación");
    }

    @Test
    void movedStudentKeepsGradesVisible() throws Exception {
        exec(db, "UPDATE student_profile SET id_group = 2 WHERE id_student = 1");
        GradeRow row = grades.sheet(1, 1, JUAN).stream().filter(r -> r.idStudent() == 1).findFirst().orElseThrow();
        assertFalse(row.inGroup());
        // Se le puede seguir corrigiendo la calificación de esa clase.
        grades.save(1, 1, List.of(new GradeInput(1, d("10"), d("10"), d("10"))), JUAN);
        assertEquals("10.00", text(db, "SELECT final_grade::text FROM grades WHERE id_student = 1 AND id_group = 1 AND id_subject = 1"));
    }

    @Test
    void rangeAndParsing() throws Exception {
        assertThrows(ServiceException.class, () -> grades.save(1, 1,
                List.of(new GradeInput(1, d("10.5"), null, null)), JUAN));
        assertThrows(ServiceException.class, () -> grades.save(1, 1,
                List.of(new GradeInput(1, d("-1"), null, null)), JUAN));
        assertThrows(ServiceException.class, () -> grades.save(1, 1,
                List.of(new GradeInput(1, d("8.555"), null, null)), JUAN));

        assertNull(GradeService.parse("  ", "x"));
        assertEquals(0, GradeService.parse("8,5", "x").compareTo(d("8.5")));
        assertThrows(ServiceException.class, () -> GradeService.parse("ocho", "x"));
        assertThrows(ServiceException.class, () -> GradeService.parse("11", "x"));
    }
}
