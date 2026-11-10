package mx.bisuni.desktop.service;

import mx.bisuni.desktop.dao.GroupDao;
import mx.bisuni.desktop.dao.ScheduleDao;
import mx.bisuni.desktop.db.Database;
import mx.bisuni.desktop.model.Role;
import mx.bisuni.desktop.model.ScheduleEntry;
import mx.bisuni.desktop.model.TimeSlot;
import mx.bisuni.desktop.model.Weekday;
import mx.bisuni.desktop.session.UserSession;
import mx.bisuni.desktop.support.PostgresTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static mx.bisuni.desktop.support.Sql.count;
import static mx.bisuni.desktop.support.Sql.exec;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Seed (periodo activo): en IT-1A (grupo 1) la materia 1 (profesor 1, 5 h) va en el bloque 1 de lunes
 * a viernes y la materia 2 (profesor 4, 4 h) en el bloque 2 de lunes a jueves. En IT-1B (grupo 2) es al
 * revés: materia 2 (profesor 4) en el bloque 1 y materia 1 (profesor 1) en el bloque 2. Las materias
 * están completas (horas = weekly_hours); los bloques 3 a 9 están libres. El grupo 6 es el IT-1A del
 * periodo cerrado 2026-1.
 */
class ScheduleServiceTest {

    private static final UserSession ADMIN =
            new UserSession(1, "admin.master@bisuni.edu", "Antonio", "Ramírez", Role.Admin, 1);
    private static final UserSession TEACHER =
            new UserSession(3, "juan.perez@bisuni.edu", "Juan", "Pérez", Role.Teacher, 1);

    private Database db;
    private ScheduleService service;
    private List<TimeSlot> slots;

    @BeforeEach
    void setUp() throws Exception {
        db = PostgresTestSupport.freshDatabase();
        service = new ScheduleService(db, new ScheduleDao(), new GroupDao());
        slots = service.timeSlots();
    }

    private TimeSlot slot(int number) {
        return slots.stream().filter(s -> s.number() == number).findFirst().orElseThrow();
    }

    @Test
    void timeSlotsCatalog() {
        assertEquals(9, slots.size());
        assertEquals("07:00 – 08:00", slot(1).range());
    }

    @Test
    void groupAndTeacherSchedules() throws Exception {
        assertEquals(9, service.forGroup(1).size());
        List<ScheduleEntry> juan = service.forTeacher(1);
        assertEquals(10, juan.size(), "materia 1 en IT-1A (5) y en IT-1B (5); el IT-1A de 2026-1 ya no cuenta");
        assertTrue(juan.stream().allMatch(e -> e.idSubject() == 1 && e.teacherName().equals("Juan Pérez")));
    }

    @Test
    void assignToEmptySlotUsesGroupTeacher() throws Exception {
        service.clear(1, Weekday.Mon, slot(2), ADMIN); // la materia 2 queda con 3 de 4 horas
        service.assign(1, Weekday.Mon, slot(7), 2, ADMIN);
        assertEquals(4, count(db, """
                SELECT id_teacher FROM schedule WHERE id_group = 1 AND day = 'Mon' AND id_slot = ?""",
                slot(7).idSlot()));
    }

    @Test
    void weeklyHoursLimit() {
        var e = assertThrows(ServiceException.class, () -> service.assign(1, Weekday.Mon, slot(8), 1, ADMIN));
        assertTrue(e.getMessage().contains("ya tiene sus 5 horas semanales"), e.getMessage());
    }

    @Test
    void assignReplacesCurrentClass() throws Exception {
        service.clear(1, Weekday.Tue, slot(1), ADMIN); // materia 1: 4 de 5
        service.clear(1, Weekday.Tue, slot(2), ADMIN); // materia 2: 3 de 4
        service.assign(1, Weekday.Tue, slot(7), 1, ADMIN);
        service.assign(1, Weekday.Tue, slot(7), 2, ADMIN); // reemplaza a la materia 1 en ese bloque
        assertEquals(1, count(db, "SELECT count(*) FROM schedule WHERE id_group = 1 AND day = 'Tue' AND id_slot = ?",
                slot(7).idSlot()));
        assertEquals(2, count(db, "SELECT id_subject FROM schedule WHERE id_group = 1 AND day = 'Tue' AND id_slot = ?",
                slot(7).idSlot()));
    }

    @Test
    void sameSubjectIsNoOp() throws Exception {
        service.assign(1, Weekday.Mon, slot(1), 1, ADMIN);
        assertEquals(9, service.forGroup(1).size());
    }

    @Test
    void teacherClashNamesTheOtherGroup() throws Exception {
        // En IT-1A, lunes bloque 1: poner la materia 2 (profesor 4), que a esa hora da clase en IT-1B.
        service.clear(1, Weekday.Mon, slot(2), ADMIN); // para que la materia 2 tenga una hora libre
        var e = assertThrows(ServiceException.class, () -> service.assign(1, Weekday.Mon, slot(1), 2, ADMIN));
        assertTrue(e.getMessage().contains("ya tiene clase el lunes de 07:00 a 08:00 en IT-1B"), e.getMessage());
        assertEquals(1, count(db, "SELECT id_subject FROM schedule WHERE id_group = 1 AND day = 'Mon' AND id_slot = ?",
                slot(1).idSlot()), "la clase original se conserva");
    }

    @Test
    void closedPeriodScheduleIsReadOnly() {
        var e = assertThrows(ServiceException.class, () -> service.clear(6, Weekday.Mon, slot(1), ADMIN));
        assertTrue(e.getMessage().contains("es historial"), e.getMessage());
    }

    @Test
    void subjectWithoutTeacherIsRejected() throws Exception {
        int subject = count(db, """
                SELECT min(id_subject) FROM subjects
                WHERE id_career = 1 AND id_subject NOT IN (SELECT id_subject FROM group_subject WHERE id_group = 1)""");
        exec(db, "INSERT INTO group_subject (id_group, id_subject) VALUES (1, " + subject + ")");
        var e = assertThrows(ServiceException.class, () -> service.assign(1, Weekday.Mon, slot(8), subject, ADMIN));
        assertTrue(e.getMessage().contains("no tiene profesor asignado"), e.getMessage());
    }

    @Test
    void subjectNotInGroupIsRejected() {
        var e = assertThrows(ServiceException.class, () -> service.assign(1, Weekday.Mon, slot(8), 9, ADMIN));
        assertTrue(e.getMessage().contains("ya no está asignada al grupo"), e.getMessage());
    }

    @Test
    void clearSlot() throws Exception {
        service.clear(1, Weekday.Fri, slot(1), ADMIN);
        assertEquals(8, service.forGroup(1).size());
        service.clear(1, Weekday.Fri, slot(1), ADMIN); // vaciar un bloque vacío no falla
        assertEquals(8, service.forGroup(1).size());
    }

    @Test
    void teacherScheduleFollowsGroupChanges() throws Exception {
        service.clear(1, Weekday.Fri, slot(1), ADMIN);
        assertEquals(9, service.forTeacher(1).size(), "el horario del profesor se deriva del de los grupos");
    }

    @Test
    void onlyAdminsCanEdit() {
        assertThrows(ServiceException.class, () -> service.assign(1, Weekday.Mon, slot(8), 1, TEACHER));
        assertThrows(ServiceException.class, () -> service.clear(1, Weekday.Mon, slot(1), TEACHER));
    }
}
