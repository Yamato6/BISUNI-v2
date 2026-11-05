package mx.bisuni.desktop.service;

import mx.bisuni.desktop.dao.AdmissionDao;
import mx.bisuni.desktop.dao.AspirantDao;
import mx.bisuni.desktop.db.Database;
import mx.bisuni.desktop.model.AspirantRow;
import mx.bisuni.desktop.model.AspirantStatus;
import mx.bisuni.desktop.model.Role;
import mx.bisuni.desktop.service.AdmissionService.Change;
import mx.bisuni.desktop.session.UserSession;
import mx.bisuni.desktop.support.PostgresTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.Map;

import static mx.bisuni.desktop.model.AspirantStatus.Accepted;
import static mx.bisuni.desktop.model.AspirantStatus.Pending;
import static mx.bisuni.desktop.model.AspirantStatus.Rejected;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Seed: aspirantes 1-4 pendientes (usuarios 63-66), 5-6 ya aceptados.
 * Admin 2 = usuario 2 (admin.control), para comprobar que approved_by sale de la sesión.
 */
class AdmissionServiceTest {

    private static final UserSession ADMIN =
            new UserSession(2, "control.school@bisuni.edu", "Beatriz", "Cortés", Role.Admin, 2);
    private static final UserSession TEACHER =
            new UserSession(3, "juan.perez@bisuni.edu", "Juan", "Pérez", Role.Teacher, 1);

    private Database db;
    private AdmissionService service;

    @BeforeEach
    void setUp() {
        db = PostgresTestSupport.freshDatabase();
        service = new AdmissionService(db, new AspirantDao(), new AdmissionDao());
    }

    @Test
    void listFiltersByStatus() throws Exception {
        assertEquals(6, service.list(null).size());
        List<AspirantRow> pending = service.list(Pending);
        assertEquals(4, pending.size());
        assertTrue(pending.stream().allMatch(r -> r.status() == Pending));
        assertEquals(2, service.list(Accepted).size());
        assertEquals("ASP-000001", service.list(null).stream()
                .filter(r -> r.curp().equals("BEXA070312MASLND01")).findFirst().orElseThrow().folio());
    }

    @Test
    void acceptingCreatesStudentAndEnrollment() throws Exception {
        int studentsBefore = count("SELECT count(*) FROM student_profile");

        var result = service.applyChanges(Map.of(1, new Change(Pending, Accepted)), ADMIN);

        assertEquals(1, result.accepted());
        assertEquals("Accepted", text("SELECT status::text FROM aspirant_profile WHERE id_aspirant = 1"));
        assertEquals("Student", text("SELECT role::text FROM users WHERE id_user = 63"));
        assertEquals(studentsBefore + 1, count("SELECT count(*) FROM student_profile"));
        assertEquals(1, count("""
                SELECT count(*) FROM student_profile s
                WHERE s.id_user = 63 AND s.id_career = 1 AND s.semester = 1 AND s.gpa = 0 AND s.id_group IS NULL"""));
        assertEquals(1, count("""
                SELECT count(*) FROM enrollment e
                JOIN student_profile s ON s.id_student = e.id_student
                WHERE e.id_aspirant = 1 AND s.id_user = 63 AND e.approved_by = 2
                  AND e.enrollment_date = CURRENT_DATE"""));
    }

    @Test
    void applyingSameChangeTwiceDoesNotDuplicate() throws Exception {
        var changes = Map.of(1, new Change(Pending, Accepted));
        service.applyChanges(changes, ADMIN);
        int students = count("SELECT count(*) FROM student_profile");
        int enrollments = count("SELECT count(*) FROM enrollment");

        // Segunda vez con la misma "foto" vieja (Pending): ya no corresponde y se rechaza limpio.
        var e = assertThrows(ServiceException.class, () -> service.applyChanges(changes, ADMIN));
        assertTrue(e.getMessage().contains("ASP-000001"), e.getMessage());

        assertEquals(students, count("SELECT count(*) FROM student_profile"));
        assertEquals(enrollments, count("SELECT count(*) FROM enrollment"));
    }

    @Test
    void emptyChangesDoNothing() throws Exception {
        assertEquals(0, service.applyChanges(Map.of(), ADMIN).total());
    }

    @Test
    void rejectingDoesNotCreateProfile() throws Exception {
        int students = count("SELECT count(*) FROM student_profile");
        var result = service.applyChanges(Map.of(2, new Change(Pending, Rejected)), ADMIN);
        assertEquals(1, result.rejected());
        assertEquals("Rejected", text("SELECT status::text FROM aspirant_profile WHERE id_aspirant = 2"));
        assertEquals("Aspirant", text("SELECT role::text FROM users WHERE id_user = 64"));
        assertEquals(students, count("SELECT count(*) FROM student_profile"));
    }

    @Test
    void rejectedCanLaterBeAccepted() throws Exception {
        service.applyChanges(Map.of(2, new Change(Pending, Rejected)), ADMIN);
        service.applyChanges(Map.of(2, new Change(Rejected, Accepted)), ADMIN);
        assertEquals(1, count("SELECT count(*) FROM enrollment WHERE id_aspirant = 2"));
    }

    @Test
    void acceptedIsFinal() throws Exception {
        var e = assertThrows(ServiceException.class,
                () -> service.applyChanges(Map.of(5, new Change(Accepted, Pending)), ADMIN));
        assertTrue(e.getMessage().contains("no se puede revertir"), e.getMessage());
        assertEquals("Accepted", text("SELECT status::text FROM aspirant_profile WHERE id_aspirant = 5"));
    }

    @Test
    void failureInBatchRollsBackEverything() throws Exception {
        int students = count("SELECT count(*) FROM student_profile");
        // El 1 es válido; el 3 trae una "foto" vieja (dice Rejected pero está Pending) y aborta el lote.
        var changes = Map.of(
                1, new Change(Pending, Accepted),
                3, new Change(Rejected, Accepted));
        assertThrows(ServiceException.class, () -> service.applyChanges(changes, ADMIN));

        assertEquals("Pending", text("SELECT status::text FROM aspirant_profile WHERE id_aspirant = 1"));
        assertEquals("Aspirant", text("SELECT role::text FROM users WHERE id_user = 63"));
        assertEquals(students, count("SELECT count(*) FROM student_profile"));
        assertEquals(0, count("SELECT count(*) FROM enrollment WHERE id_aspirant = 1"));
    }

    @Test
    void mixedBatchIsCounted() throws Exception {
        var result = service.applyChanges(Map.of(
                1, new Change(Pending, Accepted),
                2, new Change(Pending, Rejected),
                3, new Change(Pending, Accepted)), ADMIN);
        assertEquals(2, result.accepted());
        assertEquals(1, result.rejected());
        assertEquals(1, service.list(Pending).size());
    }

    @Test
    void teacherCannotProcessAdmissions() throws Exception {
        assertThrows(ServiceException.class,
                () -> service.applyChanges(Map.of(1, new Change(Pending, Accepted)), TEACHER));
        assertEquals("Pending", text("SELECT status::text FROM aspirant_profile WHERE id_aspirant = 1"));
    }

    private int count(String sql) throws Exception {
        return db.withConnection(c -> {
            try (PreparedStatement ps = c.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        });
    }

    private String text(String sql) throws Exception {
        return db.withConnection(c -> {
            try (PreparedStatement ps = c.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getString(1);
            }
        });
    }
}
