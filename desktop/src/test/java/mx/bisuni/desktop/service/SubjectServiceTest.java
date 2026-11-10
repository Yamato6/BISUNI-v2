package mx.bisuni.desktop.service;

import mx.bisuni.desktop.dao.SubjectDao;
import mx.bisuni.desktop.db.Database;
import mx.bisuni.desktop.model.RecordStatus;
import mx.bisuni.desktop.model.Role;
import mx.bisuni.desktop.model.SubjectData;
import mx.bisuni.desktop.model.SubjectRow;
import mx.bisuni.desktop.session.UserSession;
import mx.bisuni.desktop.support.PostgresTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static mx.bisuni.desktop.support.Sql.count;
import static mx.bisuni.desktop.support.Sql.text;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SubjectServiceTest {

    private static final UserSession ADMIN =
            new UserSession(1, "admin.master@bisuni.edu", "Antonio", "Ramírez", Role.Admin, 1);

    private Database db;
    private SubjectService service;

    @BeforeEach
    void setUp() {
        db = PostgresTestSupport.freshDatabase();
        service = new SubjectService(db, new SubjectDao());
    }

    @Test
    void listWithCountsAndFilters() throws Exception {
        assertEquals(count(db, "SELECT count(*) FROM subjects"), service.list(null).size());
        List<SubjectRow> it1 = service.list(new SubjectDao.Filter(1, 1, null, null));
        assertTrue(it1.stream().allMatch(s -> s.idCareer() == 1 && s.semester() == 1));
        SubjectRow s1 = service.list(null).stream().filter(s -> s.idSubject() == 1).findFirst().orElseThrow();
        assertEquals(3, s1.groupCount(), "la materia 1 la cursan IT-1A e IT-1B, y la cursó el IT-1A de 2026-1");
        assertEquals(count(db, "SELECT count(*) FROM teacher_subject WHERE id_subject = 1"), s1.teacherCount());
    }

    @Test
    void createAndDuplicateName() throws Exception {
        int id = service.create(new SubjectData("  Ética profesional ", 1, 3, 4, 3, RecordStatus.Active), ADMIN);
        assertEquals("Ética profesional", text(db, "SELECT subject_name FROM subjects WHERE id_subject = ?", id));

        var dup = assertThrows(ServiceException.class, () ->
                service.create(new SubjectData("Ética profesional", 1, 4, 4, 3, RecordStatus.Active), ADMIN));
        assertEquals("Ya existe una materia con ese nombre en la carrera.", dup.getMessage());

        // El mismo nombre en otra carrera sí se permite.
        service.create(new SubjectData("Ética profesional", 2, 3, 4, 3, RecordStatus.Active), ADMIN);
    }

    @Test
    void validation() {
        assertThrows(ServiceException.class, () ->
                service.create(new SubjectData(" ", 1, 1, 4, 3, RecordStatus.Active), ADMIN));
        assertThrows(ServiceException.class, () ->
                service.create(new SubjectData("X", 1, 13, 4, 3, RecordStatus.Active), ADMIN));
        assertThrows(ServiceException.class, () ->
                service.create(new SubjectData("X", 1, 1, 0, 3, RecordStatus.Active), ADMIN));
        assertThrows(ServiceException.class, () ->
                service.create(new SubjectData("X", 1, 1, 4, 0, RecordStatus.Active), ADMIN));
        assertThrows(ServiceException.class, () ->
                service.create(new SubjectData("X", 0, 1, 4, 3, RecordStatus.Active), ADMIN));
    }

    @Test
    void cannotChangeCareerWhenAssignedToGroups() throws Exception {
        SubjectRow s1 = service.list(null).stream().filter(s -> s.idSubject() == 1).findFirst().orElseThrow();
        var data = new SubjectData(s1.name(), 2, s1.semester(), s1.credits(), s1.weeklyHours(), s1.status());
        var e = assertThrows(ServiceException.class, () -> service.update(1, data, ADMIN));
        assertTrue(e.getMessage().contains("3 grupos"), e.getMessage());
        assertEquals(1, count(db, "SELECT id_career FROM subjects WHERE id_subject = 1"));
    }

    @Test
    void updateOtherFieldsAndCareerWhenUnassigned() throws Exception {
        int id = service.create(new SubjectData("Optativa", 1, 5, 4, 3, RecordStatus.Active), ADMIN);
        service.update(id, new SubjectData("Optativa I", 2, 6, 5, 4, RecordStatus.Inactive), ADMIN);
        assertEquals("Optativa I|2|6|5|4|Inactive", text(db, """
                SELECT subject_name || '|' || id_career || '|' || semester || '|' || credits || '|'
                       || weekly_hours || '|' || status FROM subjects WHERE id_subject = ?""", id));
    }
}
