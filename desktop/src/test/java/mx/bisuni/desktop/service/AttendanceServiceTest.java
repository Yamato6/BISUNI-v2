package mx.bisuni.desktop.service;

import mx.bisuni.desktop.dao.AttendanceDao;
import mx.bisuni.desktop.dao.TeachingDao;
import mx.bisuni.desktop.db.Database;
import mx.bisuni.desktop.model.AttendanceRow;
import mx.bisuni.desktop.model.AttendanceStatus;
import mx.bisuni.desktop.model.Role;
import mx.bisuni.desktop.model.Weekday;
import mx.bisuni.desktop.session.UserSession;
import mx.bisuni.desktop.support.PostgresTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

import static mx.bisuni.desktop.support.Sql.count;
import static mx.bisuni.desktop.support.Sql.text;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Seed: Juan Pérez (profesor 1, usuario 3) da Programming Fundamentals (materia 1) en IT-1A (grupo 1),
 * de lunes a viernes; hay asistencia registrada del 17 de agosto al 6 de noviembre de 2026.
 */
class AttendanceServiceTest {

    private static final UserSession JUAN =
            new UserSession(3, "juan.perez@bisuni.edu", "Juan", "Pérez", Role.Teacher, 1);
    private static final UserSession MARIA =
            new UserSession(6, "maria.garcia@bisuni.edu", "María", "García", Role.Teacher, 4);
    /** Lunes dentro del periodo 2026-2, sin registros en el seed. */
    private static final LocalDate MONDAY = LocalDate.of(2026, 11, 9);

    private Database db;
    private AttendanceService service;

    @BeforeEach
    void setUp() {
        db = PostgresTestSupport.freshDatabase();
        service = new AttendanceService(db, new AttendanceDao(), new TeachingDao());
    }

    @Test
    void sheetShowsMarksAndAccumulatedAbsences() throws Exception {
        var rows = service.sheet(1, 1, LocalDate.of(2026, 11, 6), JUAN);
        assertEquals(10, rows.size());
        assertTrue(rows.stream().allMatch(r -> r.status() != null && r.sessions() == 60));
        assertTrue(service.sheet(1, 1, MONDAY, JUAN).stream().allMatch(r -> r.status() == null));
        assertEquals(EnumSet.allOf(Weekday.class), service.scheduledDays(1, 1));
        assertEquals(LocalDate.of(2026, 11, 6), service.recordedDates(1, 1).getFirst());
    }

    @Test
    void savingIsAnUpsertAndUpdatesTheSummary() throws Exception {
        Map<Integer, AttendanceStatus> marks = new HashMap<>();
        service.sheet(1, 1, MONDAY, JUAN).forEach(r -> marks.put(r.idStudent(), AttendanceStatus.Present));
        marks.put(1, AttendanceStatus.Absent);
        assertEquals(10, service.save(1, 1, MONDAY, marks, JUAN));

        AttendanceRow s1 = service.sheet(1, 1, MONDAY, JUAN).stream().filter(r -> r.idStudent() == 1).findFirst().orElseThrow();
        assertEquals(AttendanceStatus.Absent, s1.status());
        assertEquals(61, s1.sessions());
        assertEquals(2, s1.absences(), "una del seed y la de hoy");

        service.save(1, 1, MONDAY, Map.of(1, AttendanceStatus.Excused), JUAN);
        assertEquals(1, count(db, "SELECT count(*) FROM attendance WHERE id_student = 1 AND id_group = 1 "
                + "AND id_subject = 1 AND class_date = '2026-11-09'"));
        assertEquals("Excused|3", text(db, "SELECT status::text || '|' || recorded_by FROM attendance "
                + "WHERE id_student = 1 AND id_group = 1 AND id_subject = 1 AND class_date = '2026-11-09'"));
    }

    @Test
    void rulesForDatesStudentsAndTeachers() {
        var future = assertThrows(ServiceException.class,
                () -> service.save(1, 1, LocalDate.now().plusDays(1), Map.of(1, AttendanceStatus.Present), JUAN));
        assertEquals("No se puede pasar lista de una fecha futura.", future.getMessage());

        var outside = assertThrows(ServiceException.class,
                () -> service.save(1, 1, LocalDate.of(2026, 7, 1), Map.of(1, AttendanceStatus.Present), JUAN));
        assertEquals("La fecha está fuera de las fechas del periodo.", outside.getMessage());

        var notMine = assertThrows(ServiceException.class,
                () -> service.save(1, 1, MONDAY, Map.of(1, AttendanceStatus.Present), MARIA));
        assertEquals("No impartes esa materia en ese grupo.", notMine.getMessage());

        var stranger = assertThrows(ServiceException.class,
                () -> service.save(1, 1, MONDAY, Map.of(21, AttendanceStatus.Present), JUAN), "alumno de BA-1A");
        assertTrue(stranger.getMessage().contains("ya no está en el grupo"), stranger.getMessage());
    }

    @Test
    void classDaysAndRisk() {
        var weekdays = EnumSet.of(Weekday.Mon, Weekday.Wed);
        assertTrue(AttendanceService.isClassDay(MONDAY, weekdays));
        assertFalse(AttendanceService.isClassDay(MONDAY.plusDays(1), weekdays));
        assertFalse(AttendanceService.isClassDay(LocalDate.of(2026, 11, 14), EnumSet.allOf(Weekday.class)), "sábado");
        assertEquals(LocalDate.of(2026, 11, 11), AttendanceService.lastClassDay(LocalDate.of(2026, 11, 14), weekdays).orElseThrow());
        assertTrue(AttendanceService.atRisk(new java.math.BigDecimal("35.4")));
        assertFalse(AttendanceService.atRisk(new java.math.BigDecimal("20.0")));
    }
}
