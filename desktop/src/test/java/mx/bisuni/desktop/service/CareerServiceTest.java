package mx.bisuni.desktop.service;

import mx.bisuni.desktop.dao.CareerDao;
import mx.bisuni.desktop.db.Database;
import mx.bisuni.desktop.model.CareerData;
import mx.bisuni.desktop.model.CareerRow;
import mx.bisuni.desktop.model.RecordStatus;
import mx.bisuni.desktop.model.Role;
import mx.bisuni.desktop.session.UserSession;
import mx.bisuni.desktop.support.PostgresTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static mx.bisuni.desktop.support.Sql.text;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Seed: 4 carreras (IT, BA, IM, IP) de 4 semestres, con materias hasta 4° y alumnos. */
class CareerServiceTest {

    private static final UserSession ADMIN =
            new UserSession(1, "admin.master@bisuni.edu", "Antonio", "Ramírez", Role.Admin, 1);
    private static final UserSession TEACHER =
            new UserSession(3, "juan.perez@bisuni.edu", "Juan", "Pérez", Role.Teacher, 1);

    private Database db;
    private CareerService service;

    @BeforeEach
    void setUp() {
        db = PostgresTestSupport.freshDatabase();
        service = new CareerService(db, new CareerDao());
    }

    private CareerRow it() throws Exception {
        return service.list(null).stream().filter(c -> c.code().equals("IT")).findFirst().orElseThrow();
    }

    @Test
    void listWithCounts() throws Exception {
        assertEquals(4, service.list(null).size());
        CareerRow it = it();
        assertEquals(8, it.subjectCount());
        assertEquals(4, it.maxSemester());
        assertTrue(it.studentCount() > 0);
    }

    @Test
    void createUppercasesCodeAndRejectsDuplicates() throws Exception {
        int id = service.create(new CareerData("  Software Engineering ", "se", 9, RecordStatus.Active), ADMIN);
        assertEquals("SE|Software Engineering", text(db,
                "SELECT code || '|' || career_name FROM career WHERE id_career = ?", id));
        var dup = assertThrows(ServiceException.class, () -> service.create(
                new CareerData("Otra", "IT", 9, RecordStatus.Active), ADMIN));
        assertEquals("Ya existe una carrera con esa clave.", dup.getMessage());
        assertThrows(ServiceException.class, () -> service.create(
                new CareerData("Mala", "I1", 9, RecordStatus.Active), ADMIN));
        assertThrows(ServiceException.class, () -> service.create(
                new CareerData("Teacher", "TT", 9, RecordStatus.Active), TEACHER));
    }

    @Test
    void codeIsFrozenOnceThereAreStudents() throws Exception {
        CareerRow it = it();
        var e = assertThrows(ServiceException.class, () -> service.update(it.idCareer(),
                new CareerData(it.name(), "TI", it.totalSemesters(), it.status()), ADMIN));
        assertTrue(e.getMessage().contains("matrícula"), e.getMessage());

        // Sin alumnos sí se puede cambiar.
        int id = service.create(new CareerData("Nueva", "NV", 6, RecordStatus.Active), ADMIN);
        service.update(id, new CareerData("Nueva", "NW", 6, RecordStatus.Inactive), ADMIN);
        assertEquals("NW|Inactive", text(db, "SELECT code || '|' || status FROM career WHERE id_career = ?", id));
    }

    @Test
    void semestersCannotDropBelowItsSubjects() throws Exception {
        CareerRow it = it();
        var e = assertThrows(ServiceException.class, () -> service.update(it.idCareer(),
                new CareerData(it.name(), it.code(), 3, it.status()), ADMIN));
        assertTrue(e.getMessage().contains("4° semestre"), e.getMessage());
        service.update(it.idCareer(), new CareerData(it.name(), it.code(), 10, it.status()), ADMIN);
        assertEquals(10, it().totalSemesters());
    }
}
