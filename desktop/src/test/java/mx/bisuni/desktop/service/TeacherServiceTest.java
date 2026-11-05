package mx.bisuni.desktop.service;

import mx.bisuni.desktop.dao.TeacherDao;
import mx.bisuni.desktop.dao.UserDao;
import mx.bisuni.desktop.db.Database;
import mx.bisuni.desktop.model.RecordStatus;
import mx.bisuni.desktop.model.Role;
import mx.bisuni.desktop.model.TeacherRow;
import mx.bisuni.desktop.model.TeacherSubject;
import mx.bisuni.desktop.model.UserData;
import mx.bisuni.desktop.session.UserSession;
import mx.bisuni.desktop.support.PostgresTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static mx.bisuni.desktop.support.Sql.count;
import static mx.bisuni.desktop.support.Sql.intOrNull;
import static mx.bisuni.desktop.support.Sql.text;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TeacherServiceTest {

    private static final UserSession ADMIN =
            new UserSession(1, "admin.master@bisuni.edu", "Antonio", "Ramírez", Role.Admin, 1);

    private Database db;
    private TeacherService service;

    @BeforeEach
    void setUp() {
        db = PostgresTestSupport.freshDatabase();
        service = new TeacherService(db, new TeacherDao(), new UserDao());
    }

    @Test
    void listWithSubjectCountAndSearch() throws Exception {
        List<TeacherRow> all = service.list(null, null);
        assertEquals(count(db, "SELECT count(*) FROM teacher_profile"), all.size());
        TeacherRow juan = service.list(null, "juan.perez").getFirst();
        assertEquals(count(db, "SELECT count(*) FROM teacher_subject WHERE id_teacher = 1"), juan.subjectCount());
        assertFalse(service.list(null, "Software").isEmpty(), "busca también por especialidad");
        assertTrue(service.list(RecordStatus.Inactive, null).isEmpty());
    }

    @Test
    void updateProfile() throws Exception {
        service.update(1, new UserData("jperez", "Juan", "Pérez", "juan.perez@bisuni.edu", RecordStatus.Active),
                " Arquitectura de software ", "", ADMIN);
        assertEquals("Arquitectura de software", text(db, "SELECT specialty FROM teacher_profile WHERE id_teacher = 1"));
        assertNull(text(db, "SELECT phone FROM teacher_profile WHERE id_teacher = 1"));
    }

    @Test
    void subjectsShowGroupsWhereTaught() throws Exception {
        Integer taughtTeacher = intOrNull(db, "SELECT min(id_teacher) FROM group_subject WHERE id_teacher IS NOT NULL");
        assertNotNull(taughtTeacher);
        List<TeacherSubject> subjects = service.subjects(taughtTeacher);
        assertTrue(subjects.stream().anyMatch(TeacherSubject::isAssignedToGroups));
    }

    @Test
    void addAndRemoveUnassignedSubject() throws Exception {
        Integer free = intOrNull(db, """
                SELECT min(id_subject) FROM subjects
                WHERE id_subject NOT IN (SELECT id_subject FROM teacher_subject WHERE id_teacher = 1)""");
        service.addSubject(1, free, ADMIN);
        assertEquals(1, count(db, "SELECT count(*) FROM teacher_subject WHERE id_teacher = 1 AND id_subject = ?", free));

        var dup = assertThrows(ServiceException.class, () -> service.addSubject(1, free, ADMIN));
        assertEquals("El profesor ya tiene asignada esa materia.", dup.getMessage());

        service.removeSubject(1, free, ADMIN);
        assertEquals(0, count(db, "SELECT count(*) FROM teacher_subject WHERE id_teacher = 1 AND id_subject = ?", free));
    }

    @Test
    void cannotRemoveSubjectTaughtInAGroup() throws Exception {
        int teacher = count(db, "SELECT id_teacher FROM group_subject WHERE id_teacher IS NOT NULL ORDER BY 1 LIMIT 1");
        int subject = count(db, "SELECT id_subject FROM group_subject WHERE id_teacher = ? ORDER BY 1 LIMIT 1", teacher);
        var e = assertThrows(ServiceException.class, () -> service.removeSubject(teacher, subject, ADMIN));
        assertTrue(e.getMessage().startsWith("El profesor imparte esa materia"), e.getMessage());
        assertEquals(1, count(db, "SELECT count(*) FROM teacher_subject WHERE id_teacher = ? AND id_subject = ?",
                teacher, subject));
    }
}
