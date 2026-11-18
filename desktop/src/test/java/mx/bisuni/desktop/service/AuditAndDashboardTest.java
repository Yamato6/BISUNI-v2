package mx.bisuni.desktop.service;

import mx.bisuni.desktop.dao.AuditDao;
import mx.bisuni.desktop.dao.DashboardDao;
import mx.bisuni.desktop.dao.GradeDao;
import mx.bisuni.desktop.dao.StudentDao;
import mx.bisuni.desktop.dao.TeachingDao;
import mx.bisuni.desktop.dao.UserDao;
import mx.bisuni.desktop.db.Database;
import mx.bisuni.desktop.model.AuditRow;
import mx.bisuni.desktop.model.Dashboard;
import mx.bisuni.desktop.model.GradeInput;
import mx.bisuni.desktop.model.Role;
import mx.bisuni.desktop.session.UserSession;
import mx.bisuni.desktop.support.PostgresTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static mx.bisuni.desktop.support.Sql.exec;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuditAndDashboardTest {

    private static final UserSession ADMIN =
            new UserSession(1, "admin.master@bisuni.edu", "Antonio", "Ramírez", Role.Admin, 1);
    private static final UserSession JUAN =
            new UserSession(3, "juan.perez@bisuni.edu", "Juan", "Pérez", Role.Teacher, 1);
    private static final AuditDao.Filter ALL = new AuditDao.Filter(null, null, null, null, 0);

    private Database db;
    private AuditService audit;
    private DashboardService dashboard;

    @BeforeEach
    void setUp() {
        db = PostgresTestSupport.freshDatabase();
        audit = new AuditService(db, new AuditDao());
        dashboard = new DashboardService(db, new DashboardDao());
    }

    // ---------------- Bitácora ----------------

    @Test
    void auditShowsWhoChangedWhatInPlainSpanish() throws Exception {
        assertTrue(audit.list(ALL, ADMIN).isEmpty(), "el seed no deja rastro en la bitácora");

        new GradeService(db, new GradeDao(), new TeachingDao())
                .save(1, 1, List.of(new GradeInput(2, new BigDecimal("10"), new BigDecimal("9"), new BigDecimal("9"))), JUAN);
        new UserService(db, new UserDao(), new StudentDao()).resetPassword(13, ADMIN);

        List<AuditRow> rows = audit.list(ALL, ADMIN);
        assertEquals(2, rows.size());
        AuditRow reset = rows.getFirst();
        assertEquals("Antonio Ramírez", reset.actorName());
        assertEquals("Carlos Hernández (student001@bisuni.edu)", reset.subject());
        assertEquals("Debe cambiar contraseña: No → Sí\nContraseña: cambiada", AuditService.describe(reset));

        AuditRow grade = rows.get(1);
        assertEquals("Juan Pérez", grade.actorName());
        assertEquals("Daniela Torres · IT-1A · Programming Fundamentals", grade.subject());
        assertEquals("Parcial 1: 8.50 → 10.00\nParcial 2: 8.50 → 9.00\nParcial 3: 8.50 → 9.00", AuditService.describe(grade));
        assertEquals("Calificaciones", AuditService.entityLabel(grade.entity()));
        assertEquals("Cambio", AuditService.actionLabel(grade.action()));
    }

    @Test
    void auditFilters() throws Exception {
        new UserService(db, new UserDao(), new StudentDao()).resetPassword(13, ADMIN);
        exec(db, "UPDATE career SET total_semesters = 5 WHERE id_career = 1"); // sin sesión: actor nulo
        assertEquals(1, audit.list(new AuditDao.Filter(null, null, "career", null, 0), ADMIN).size());
        assertNull(audit.list(new AuditDao.Filter(null, null, "career", null, 0), ADMIN).getFirst().actorName());
        assertEquals(1, audit.list(new AuditDao.Filter(null, null, null, "student001", 0), ADMIN).size());
        assertEquals(2, audit.list(new AuditDao.Filter(LocalDate.now(), LocalDate.now(), null, null, 0), ADMIN).size());
        assertTrue(audit.list(new AuditDao.Filter(LocalDate.now().plusDays(1), null, null, null, 0), ADMIN).isEmpty());
        assertThrows(ServiceException.class, () -> audit.list(ALL, JUAN));
        assertThrows(ServiceException.class, () -> audit.list(
                new AuditDao.Filter(LocalDate.now(), LocalDate.now().minusDays(1), null, null, 0), ADMIN));
    }

    // ---------------- Panel ----------------

    @Test
    void dashboardSummarizesTheActivePeriod() throws Exception {
        Dashboard d = dashboard.load(ADMIN);
        assertEquals("2026-2", d.activePeriod());
        assertEquals(4, d.pendingAspirants());
        assertEquals(61, d.activeStudents());
        assertEquals(6, d.groups());
        assertEquals(12, d.openActas());
        assertEquals(30, d.studentsByCareer().stream().filter(c -> c.career().equals("Information Technology"))
                .findFirst().orElseThrow().students());

        Dashboard.FailingSubject worst = d.failing().getFirst();
        assertEquals("Discrete Mathematics|IT-1A|2026-1|3|10", String.join("|", worst.subject(), worst.group(),
                worst.period(), String.valueOf(worst.failed()), String.valueOf(worst.graded())));
        assertEquals(30, worst.pct());

        assertTrue(d.missingTeacher().isEmpty());
        assertTrue(d.incompleteSchedule().isEmpty());
        assertEquals(2, d.atRisk().size());
        assertTrue(d.atRisk().stream().allMatch(r -> r.name().equals("Fuentes, Rodrigo")));
    }

    @Test
    void dashboardAlerts() throws Exception {
        exec(db, "INSERT INTO group_subject (id_group, id_subject) VALUES (1, 3)");      // sin profesor ni horario
        exec(db, "DELETE FROM schedule WHERE id_group = 2 AND id_subject = 1 AND day = 'Fri'");
        Dashboard d = dashboard.load(ADMIN);
        assertEquals(List.of("IT-1A · Database Basics"), d.missingTeacher());
        assertEquals(List.of("IT-1A · Database Basics (0 de 4 h)", "IT-1B · Programming Fundamentals (4 de 5 h)"),
                d.incompleteSchedule());
        assertThrows(ServiceException.class, () -> dashboard.load(JUAN));
    }
}
