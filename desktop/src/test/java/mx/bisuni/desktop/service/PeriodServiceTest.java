package mx.bisuni.desktop.service;

import mx.bisuni.desktop.dao.PeriodDao;
import mx.bisuni.desktop.db.Database;
import mx.bisuni.desktop.model.Period;
import mx.bisuni.desktop.model.PeriodData;
import mx.bisuni.desktop.model.PeriodStatus;
import mx.bisuni.desktop.model.Role;
import mx.bisuni.desktop.session.UserSession;
import mx.bisuni.desktop.support.PostgresTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static mx.bisuni.desktop.support.Sql.count;
import static mx.bisuni.desktop.support.Sql.exec;
import static mx.bisuni.desktop.support.Sql.text;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Seed: 2026-1 (id 1) cerrado y 2026-2 (id 2) activo con la convocatoria abierta. */
class PeriodServiceTest {

    private static final UserSession ADMIN =
            new UserSession(2, "control.school@bisuni.edu", "Beatriz", "Cortés", Role.Admin, 2);
    private static final UserSession TEACHER =
            new UserSession(3, "juan.perez@bisuni.edu", "Juan", "Pérez", Role.Teacher, 1);
    private static final PeriodData NEXT =
            new PeriodData("2027-1", LocalDate.of(2027, 1, 11), LocalDate.of(2027, 6, 25));

    private Database db;
    private PeriodService service;

    @BeforeEach
    void setUp() {
        db = PostgresTestSupport.freshDatabase();
        service = new PeriodService(db, new PeriodDao());
    }

    @Test
    void listAndActive() throws Exception {
        var all = service.list();
        assertEquals(2, all.size());
        assertEquals("2026-2", all.getFirst().name(), "el más reciente primero");
        Period active = service.active().orElseThrow();
        assertEquals("2026-2", active.name());
        assertTrue(active.admissionsOpen());
        assertEquals(6, active.groupCount());
    }

    @Test
    void createValidatesDatesAndOverlap() throws Exception {
        int id = service.create(NEXT, ADMIN);
        assertEquals("Planned", text(db, "SELECT status::text FROM academic_period WHERE id_period = ?", id));

        var overlap = assertThrows(ServiceException.class, () -> service.create(
                new PeriodData("2026-V", LocalDate.of(2026, 12, 1), LocalDate.of(2027, 1, 5)), ADMIN));
        assertTrue(overlap.getMessage().contains("2026-2"), overlap.getMessage());

        assertThrows(ServiceException.class, () -> service.create(
                new PeriodData("X", LocalDate.of(2028, 2, 1), LocalDate.of(2028, 1, 1)), ADMIN));
        var dup = assertThrows(ServiceException.class, () -> service.create(
                new PeriodData("2027-1", LocalDate.of(2028, 1, 10), LocalDate.of(2028, 6, 1)), ADMIN));
        assertEquals("Ya existe un periodo con ese nombre.", dup.getMessage());
        assertThrows(ServiceException.class, () -> service.create(NEXT, TEACHER));
    }

    @Test
    void closingRequiresAllActasClosed() throws Exception {
        var e = assertThrows(ServiceException.class, () -> service.close(2, ADMIN));
        assertTrue(e.getMessage().contains("12 actas abiertas") && e.getMessage().contains("BA-1A · Basic Accounting")
                && e.getMessage().contains("… y 6 más"), e.getMessage());
        assertEquals("Active", text(db, "SELECT status::text FROM academic_period WHERE id_period = 2"));

        exec(db, "UPDATE group_subject SET grades_closed_at = now(), grades_closed_by = 3 "
                + "WHERE id_group IN (SELECT id_group FROM school_groups WHERE id_period = 2)");
        service.close(2, ADMIN);
        assertEquals("Closed", text(db, "SELECT status::text FROM academic_period WHERE id_period = 2"));
        assertEquals(0, count(db, "SELECT count(*) FROM academic_period WHERE admissions_open"),
                "cerrar el periodo cierra su convocatoria");
        assertFalse(service.active().isPresent());
    }

    @Test
    void activatingClosesTheCurrentPeriod() throws Exception {
        int next = service.create(NEXT, ADMIN);
        assertThrows(ServiceException.class, () -> service.activate(next, ADMIN), "2026-2 tiene actas abiertas");

        exec(db, "UPDATE group_subject SET grades_closed_at = now(), grades_closed_by = 3 "
                + "WHERE id_group IN (SELECT id_group FROM school_groups WHERE id_period = 2)");
        service.activate(next, ADMIN);
        assertEquals("2027-1", service.active().orElseThrow().name());
        assertEquals("Closed", text(db, "SELECT status::text FROM academic_period WHERE id_period = 2"));

        var again = assertThrows(ServiceException.class, () -> service.activate(1, ADMIN));
        assertTrue(again.getMessage().contains("planeado"), again.getMessage());
    }

    @Test
    void closedPeriodIsReadOnly() {
        assertThrows(ServiceException.class, () -> service.update(1, NEXT, ADMIN));
        assertThrows(ServiceException.class, () -> service.setAdmissionsOpen(1, true, ADMIN));
    }

    @Test
    void changesAreAuditedWithTheActor() throws Exception {
        service.setAdmissionsOpen(2, false, ADMIN);
        assertEquals(1, count(db, """
                SELECT count(*) FROM audit_log
                WHERE entity = 'academic_period' AND entity_id = '2' AND actor_user = 2
                  AND details -> 'admissions_open' ->> 'despues' = 'false'"""));
        assertEquals(PeriodStatus.Active, service.active().orElseThrow().status());
    }
}
