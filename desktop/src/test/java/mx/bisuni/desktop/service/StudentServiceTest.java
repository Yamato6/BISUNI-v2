package mx.bisuni.desktop.service;

import mx.bisuni.desktop.dao.StudentDao;
import mx.bisuni.desktop.dao.UserDao;
import mx.bisuni.desktop.db.Database;
import mx.bisuni.desktop.model.RecordStatus;
import mx.bisuni.desktop.model.Role;
import mx.bisuni.desktop.model.StudentRow;
import mx.bisuni.desktop.model.StudentStatus;
import mx.bisuni.desktop.model.StudentUpdate;
import mx.bisuni.desktop.session.UserSession;
import mx.bisuni.desktop.support.PostgresTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static mx.bisuni.desktop.support.Sql.count;
import static mx.bisuni.desktop.support.Sql.intOrNull;
import static mx.bisuni.desktop.support.Sql.text;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StudentServiceTest {

    private static final UserSession ADMIN =
            new UserSession(1, "admin.master@bisuni.edu", "Antonio", "Ramírez", Role.Admin, 1);

    private Database db;
    private StudentService service;

    @BeforeEach
    void setUp() {
        db = PostgresTestSupport.freshDatabase();
        service = new StudentService(db, new StudentDao(), new UserDao());
    }

    /** student001 (usuario 13) es el alumno 1 del seed: tiene grupo y calificaciones. */
    private StudentRow student1() throws Exception {
        return service.list(new StudentDao.Filter(null, null, false, null, "student001")).getFirst();
    }

    @Test
    void listFilters() throws Exception {
        int all = count(db, "SELECT count(*) FROM student_profile");
        assertEquals(all, service.list(null).size());

        int itStudents = count(db, "SELECT count(*) FROM student_profile WHERE id_career = 1");
        var it = service.list(new StudentDao.Filter(1, null, false, null, null));
        assertEquals(itStudents, it.size());
        assertTrue(it.stream().allMatch(s -> s.idCareer() == 1));

        var noGroup = service.list(new StudentDao.Filter(null, null, true, null, null));
        assertEquals(count(db, "SELECT count(*) FROM student_profile WHERE id_group IS NULL"), noGroup.size());
        assertTrue(noGroup.stream().allMatch(s -> s.idGroup() == null));

        StudentRow s1 = student1();
        var byGroup = service.list(new StudentDao.Filter(null, s1.idGroup(), false, null, null));
        assertTrue(byGroup.stream().allMatch(s -> s.idGroup().equals(s1.idGroup())));
    }

    @Test
    void changingGroupKeepsGrades() throws Exception {
        StudentRow s = student1();
        int gradesBefore = count(db, "SELECT count(*) FROM grades WHERE id_student = ?", s.idStudent());
        assertTrue(gradesBefore > 0, "el seed debe traer calificaciones para student001");

        Integer otherGroup = intOrNull(db, """
                SELECT min(id_group) FROM school_groups WHERE id_career = ? AND id_group <> ?""",
                s.idCareer(), s.idGroup());
        service.update(s.idStudent(), new StudentUpdate(s.userData(), s.idCareer(), 2, otherGroup), ADMIN);

        assertEquals(otherGroup, intOrNull(db, "SELECT id_group FROM student_profile WHERE id_student = ?", s.idStudent()));
        assertEquals(2, count(db, "SELECT semester FROM student_profile WHERE id_student = ?", s.idStudent()));
        assertEquals(gradesBefore, count(db, "SELECT count(*) FROM grades WHERE id_student = ?", s.idStudent()));
    }

    @Test
    void groupFromAnotherCareerIsRejected() throws Exception {
        StudentRow s = student1();
        Integer foreignGroup = intOrNull(db, "SELECT min(id_group) FROM school_groups WHERE id_career <> ?", s.idCareer());
        var e = assertThrows(ServiceException.class, () -> service.update(s.idStudent(),
                new StudentUpdate(s.userData(), s.idCareer(), s.semester(), foreignGroup), ADMIN));
        assertEquals("El grupo seleccionado no pertenece a la carrera del alumno.", e.getMessage());
    }

    @Test
    void changingCareerWithoutGroup() throws Exception {
        StudentRow s = student1();
        int newCareer = s.idCareer() == 1 ? 2 : 1;
        service.update(s.idStudent(), new StudentUpdate(s.userData(), newCareer, 1, null), ADMIN);
        assertEquals(newCareer, count(db, "SELECT id_career FROM student_profile WHERE id_student = ?", s.idStudent()));
        assertNull(intOrNull(db, "SELECT id_group FROM student_profile WHERE id_student = ?", s.idStudent()));
    }

    @Test
    void updatesUserDataToo() throws Exception {
        StudentRow s = student1();
        var data = new mx.bisuni.desktop.model.UserData(s.username(), "Carlos Alberto", s.lastName(), s.email(),
                RecordStatus.Inactive);
        service.update(s.idStudent(), new StudentUpdate(data, s.idCareer(), s.semester(), s.idGroup()), ADMIN);
        assertEquals("Carlos Alberto", text(db, "SELECT first_name FROM users WHERE id_user = ?", s.idUser()));
        assertEquals("Inactive", text(db, "SELECT status::text FROM users WHERE id_user = ?", s.idUser()));
    }

    @Test
    void gpaIsComputedFromFinalGrades() throws Exception {
        StudentRow s = student1();
        // Finales 8.17 en las dos materias de IT-1A (ver seed).
        assertEquals(0, s.gpa().compareTo(new BigDecimal("8.17")));
        assertEquals("2026IT0011", s.enrollmentNumber());
        assertEquals(StudentStatus.Active, s.academicStatus());
        // Aceptado sin calificaciones todavía: sin promedio.
        StudentRow fresh = service.list(new StudentDao.Filter(null, null, false, null, "aspirant05")).getFirst();
        assertNull(fresh.gpa());
        // También se busca por matrícula.
        assertEquals(1, service.list(new StudentDao.Filter(null, null, false, null, "2026it0011")).size());
    }
}
