package mx.bisuni.desktop.service;

import mx.bisuni.desktop.dao.GroupDao;
import mx.bisuni.desktop.dao.StudentDao;
import mx.bisuni.desktop.dao.SubjectDao;
import mx.bisuni.desktop.db.Database;
import mx.bisuni.desktop.model.GroupData;
import mx.bisuni.desktop.model.GroupRow;
import mx.bisuni.desktop.model.GroupSubjectRow;
import mx.bisuni.desktop.model.RecordStatus;
import mx.bisuni.desktop.model.Role;
import mx.bisuni.desktop.session.UserSession;
import mx.bisuni.desktop.support.PostgresTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static mx.bisuni.desktop.support.Sql.count;
import static mx.bisuni.desktop.support.Sql.exec;
import static mx.bisuni.desktop.support.Sql.intOrNull;
import static mx.bisuni.desktop.support.Sql.text;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Seed: IT-1A (grupo 1) e IT-1B (grupo 2), carrera 1. Materia 1 con profesor 1 y materia 2 con
 * profesor 4 en ambos grupos. Horario: en IT-1A la materia 1 va en los bloques 1, 3 y 6 de cada
 * día; en IT-1B la materia 2 (profesor 4) va en esos mismos bloques. Hay calificaciones solo en IT-1A.
 */
class GroupServiceTest {

    private static final UserSession ADMIN =
            new UserSession(1, "admin.master@bisuni.edu", "Antonio", "Ramírez", Role.Admin, 1);

    private Database db;
    private GroupService service;

    @BeforeEach
    void setUp() {
        db = PostgresTestSupport.freshDatabase();
        service = new GroupService(db, new GroupDao(), new SubjectDao(), new StudentDao());
    }

    private GroupRow group(int id) throws Exception {
        return service.find(id).orElseThrow();
    }

    // ---------------- Grupo ----------------

    @Test
    void listWithCounts() throws Exception {
        GroupRow g1 = group(1);
        assertEquals("IT-1A", g1.name());
        assertEquals(2, g1.subjectCount());
        assertEquals(0, g1.unassignedCount());
        assertEquals(count(db, "SELECT count(*) FROM student_profile WHERE id_group = 1"), g1.studentCount());
        assertTrue(g1.tutorName() != null && !g1.tutorName().isBlank());
        assertEquals(2, service.list(new GroupDao.Filter(1, 1, null, null)).size());
        assertEquals(1, service.list(new GroupDao.Filter(null, null, null, "1b")).size());
    }

    @Test
    void createUppercasesAndRejectsDuplicates() throws Exception {
        int id = service.create(new GroupData(" it-1c ", 1, 1, null, RecordStatus.Active), ADMIN);
        assertEquals("IT-1C", text(db, "SELECT group_name FROM school_groups WHERE id_group = ?", id));
        var e = assertThrows(ServiceException.class,
                () -> service.create(new GroupData("IT-1A", 1, 1, null, RecordStatus.Active), ADMIN));
        assertEquals("Ya existe un grupo con ese nombre.", e.getMessage());
    }

    @Test
    void careerChangeOnlyWhenEmpty() throws Exception {
        var e = assertThrows(ServiceException.class,
                () -> service.update(1, new GroupData("IT-1A", 2, 1, 1, RecordStatus.Active), ADMIN));
        assertTrue(e.getMessage().contains("no se puede cambiar de carrera"), e.getMessage());

        int empty = service.create(new GroupData("TMP-1", 1, 1, null, RecordStatus.Active), ADMIN);
        service.update(empty, new GroupData("TMP-1", 2, 3, 6, RecordStatus.Inactive), ADMIN);
        assertEquals("2|3|6|Inactive", text(db, """
                SELECT id_career || '|' || semester || '|' || id_tutor || '|' || status
                FROM school_groups WHERE id_group = ?""", empty));
    }

    // ---------------- Materias y profesores ----------------

    @Test
    void subjectsShowTeacherScheduleAndGrades() throws Exception {
        GroupSubjectRow s1 = service.subjects(1).stream().filter(s -> s.idSubject() == 1).findFirst().orElseThrow();
        assertEquals(1, s1.idTeacher());
        assertEquals(15, s1.scheduledHours(), "3 bloques x 5 días");
        assertTrue(s1.gradeCount() > 0);
    }

    @Test
    void availableSubjectsAreSameCareerAndNotAssigned() throws Exception {
        var available = service.availableSubjects(1);
        assertFalse(available.isEmpty());
        assertTrue(available.stream().allMatch(s -> s.idCareer() == 1));
        assertTrue(available.stream().noneMatch(s -> s.idSubject() == 1 || s.idSubject() == 2));
    }

    @Test
    void addSubjectFromOtherCareerIsRejected() throws Exception {
        int foreign = count(db, "SELECT min(id_subject) FROM subjects WHERE id_career <> 1");
        var e = assertThrows(ServiceException.class, () -> service.addSubject(1, foreign, ADMIN));
        assertTrue(e.getMessage().contains("el grupo es de"), e.getMessage());
    }

    @Test
    void addSubjectAndDuplicate() throws Exception {
        int subject = service.availableSubjects(1).getFirst().idSubject();
        service.addSubject(1, subject, ADMIN);
        assertEquals(1, group(1).unassignedCount());
        var e = assertThrows(ServiceException.class, () -> service.addSubject(1, subject, ADMIN));
        assertEquals("El grupo ya tiene esa materia.", e.getMessage());
    }

    @Test
    void removeSubjectWithGradesIsRejected() {
        var e = assertThrows(ServiceException.class, () -> service.removeSubject(1, 1, ADMIN));
        assertEquals("La materia ya tiene calificaciones capturadas en este grupo; no se puede quitar.", e.getMessage());
    }

    @Test
    void removeSubjectDeletesItsSchedule() throws Exception {
        assertEquals(15, count(db, "SELECT count(*) FROM schedule WHERE id_group = 2 AND id_subject = 1"));
        service.removeSubject(2, 1, ADMIN);
        assertEquals(0, count(db, "SELECT count(*) FROM group_subject WHERE id_group = 2 AND id_subject = 1"));
        assertEquals(0, count(db, "SELECT count(*) FROM schedule WHERE id_group = 2 AND id_subject = 1"));
    }

    @Test
    void assignTeacherCascadesToSchedule() throws Exception {
        // Profesor nuevo, habilitado para la materia 1 y sin clases: no hay choques.
        int teacher = count(db, """
                SELECT min(id_teacher) FROM teacher_profile
                WHERE id_teacher NOT IN (SELECT id_teacher FROM schedule)""");
        exec(db, "INSERT INTO teacher_subject (id_teacher, id_subject) VALUES (" + teacher + ", 1)");
        assertTrue(service.eligibleTeachers(1).stream().anyMatch(t -> t.idTeacher() == teacher));

        service.assignTeacher(1, 1, teacher, ADMIN);
        assertEquals(15, count(db, "SELECT count(*) FROM schedule WHERE id_group = 1 AND id_subject = 1 AND id_teacher = ?",
                teacher));
    }

    @Test
    void assignNotEligibleTeacherIsRejected() throws Exception {
        int notEligible = count(db, """
                SELECT min(id_teacher) FROM teacher_profile
                WHERE id_teacher NOT IN (SELECT id_teacher FROM teacher_subject WHERE id_subject = 1)""");
        var e = assertThrows(ServiceException.class, () -> service.assignTeacher(1, 1, notEligible, ADMIN));
        assertTrue(e.getMessage().startsWith("Ese profesor no está habilitado"), e.getMessage());
        assertEquals(1, count(db, "SELECT id_teacher FROM group_subject WHERE id_group = 1 AND id_subject = 1"));
    }

    @Test
    void assignTeacherWithScheduleClashIsRejected() throws Exception {
        // El profesor 4 da la materia 2 en IT-1B justo en los bloques de la materia 1 de IT-1A.
        exec(db, "INSERT INTO teacher_subject (id_teacher, id_subject) VALUES (4, 1)");
        var e = assertThrows(ServiceException.class, () -> service.assignTeacher(1, 1, 4, ADMIN));
        assertTrue(e.getMessage().startsWith("El profesor ya tiene clase"), e.getMessage());
        assertEquals(1, count(db, "SELECT id_teacher FROM group_subject WHERE id_group = 1 AND id_subject = 1"));
    }

    @Test
    void cannotUnassignTeacherWhileScheduled() throws Exception {
        var e = assertThrows(ServiceException.class, () -> service.assignTeacher(1, 1, null, ADMIN));
        assertTrue(e.getMessage().contains("15 clases"), e.getMessage());

        int subject = service.availableSubjects(1).getFirst().idSubject();
        service.addSubject(1, subject, ADMIN);
        int teacher = intOrNull(db, "SELECT min(id_teacher) FROM teacher_profile");
        exec(db, "INSERT INTO teacher_subject (id_teacher, id_subject) VALUES (" + teacher + ", " + subject + ")");
        service.assignTeacher(1, subject, teacher, ADMIN);
        service.assignTeacher(1, subject, null, ADMIN); // sin horario: sí se puede
        assertNull(intOrNull(db, "SELECT id_teacher FROM group_subject WHERE id_group = 1 AND id_subject = ?", subject));
    }

    // ---------------- Alumnos ----------------

    @Test
    void addAndRemoveStudents() throws Exception {
        var candidates = service.candidateStudents(1);
        assertFalse(candidates.isEmpty());
        assertTrue(candidates.stream().allMatch(s -> s.idCareer() == 1 && !Integer.valueOf(1).equals(s.idGroup())));

        int student = candidates.getFirst().idStudent();
        int grades = count(db, "SELECT count(*) FROM grades WHERE id_student = ?", student);
        service.addStudent(1, student, ADMIN);
        assertTrue(service.students(1).stream().anyMatch(s -> s.idStudent() == student));

        service.removeStudent(1, student, ADMIN);
        assertNull(intOrNull(db, "SELECT id_group FROM student_profile WHERE id_student = ?", student));
        assertEquals(grades, count(db, "SELECT count(*) FROM grades WHERE id_student = ?", student));
    }

    @Test
    void studentFromOtherCareerIsRejected() throws Exception {
        int other = count(db, "SELECT min(id_student) FROM student_profile WHERE id_career <> 1");
        var e = assertThrows(ServiceException.class, () -> service.addStudent(1, other, ADMIN));
        assertEquals("El alumno es de otra carrera que el grupo.", e.getMessage());
    }
}
