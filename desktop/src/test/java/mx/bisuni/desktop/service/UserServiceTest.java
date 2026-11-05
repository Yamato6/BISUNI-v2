package mx.bisuni.desktop.service;

import mx.bisuni.desktop.dao.StudentDao;
import mx.bisuni.desktop.dao.UserDao;
import mx.bisuni.desktop.db.Database;
import mx.bisuni.desktop.model.ProfileData;
import mx.bisuni.desktop.model.RecordStatus;
import mx.bisuni.desktop.model.Role;
import mx.bisuni.desktop.model.UserData;
import mx.bisuni.desktop.model.UserRow;
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

class UserServiceTest {

    private static final UserSession ADMIN =
            new UserSession(1, "admin.master@bisuni.edu", "Antonio", "Ramírez", Role.Admin, 1);
    private static final UserSession TEACHER =
            new UserSession(3, "juan.perez@bisuni.edu", "Juan", "Pérez", Role.Teacher, 1);
    private static final char[] PASSWORD = "NuevaClave2025".toCharArray();

    private Database db;
    private UserService service;
    private AuthService auth;

    @BeforeEach
    void setUp() {
        db = PostgresTestSupport.freshDatabase();
        service = new UserService(db, new UserDao(), new StudentDao());
        auth = new AuthService(db, new UserDao());
    }

    private static UserData data(String username, String email) {
        return new UserData(username, "Nora", "Quintero", email, RecordStatus.Active);
    }

    @Test
    void listFiltersByRoleStatusAndSearch() throws Exception {
        assertEquals(2, service.list(Role.Admin, null, null).size());
        assertEquals(10, service.list(Role.Teacher, null, null).size());
        List<UserRow> found = service.list(null, null, "pérez");
        assertTrue(found.stream().anyMatch(u -> u.email().equals("juan.perez@bisuni.edu")));
        // Los comodines de LIKE se tratan como texto literal.
        assertEquals(0, service.list(null, null, "%").size());
        assertEquals(0, service.list(null, RecordStatus.Inactive, null).size());
    }

    @Test
    void createTeacherWithProfileAndUsableLogin() throws Exception {
        int id = service.create(data("  NQuintero ", " Nora.Quintero@BISUNI.edu "), PASSWORD,
                new ProfileData.Teacher(" Redes ", "449 555 0000"), ADMIN);

        assertEquals("nquintero", text(db, "SELECT username FROM users WHERE id_user = ?", id));
        assertEquals("nora.quintero@bisuni.edu", text(db, "SELECT email FROM users WHERE id_user = ?", id));
        assertEquals("Teacher", text(db, "SELECT role::text FROM users WHERE id_user = ?", id));
        assertEquals("Redes", text(db, "SELECT specialty FROM teacher_profile WHERE id_user = ?", id));

        var session = auth.login("nora.quintero@bisuni.edu", PASSWORD);
        assertEquals(Role.Teacher, session.role());
    }

    @Test
    void createAdminAndStudent() throws Exception {
        int admin = service.create(data("nadmin", "nadmin@bisuni.edu"), PASSWORD, new ProfileData.Admin(""), ADMIN);
        assertEquals(1, count(db, "SELECT count(*) FROM admin_profile WHERE id_user = ? AND position IS NULL", admin));

        int student = service.create(data("nalumna", "nalumna@bisuni.edu"), PASSWORD,
                new ProfileData.Student(2, 3), ADMIN);
        assertEquals(1, count(db, """
                SELECT count(*) FROM student_profile
                WHERE id_user = ? AND id_career = 2 AND semester = 3 AND id_group IS NULL""", student));
    }

    @Test
    void duplicateEmailOrUsernameGiveClearMessages() throws Exception {
        var e1 = assertThrows(ServiceException.class, () -> service.create(
                data("otro", "JUAN.PEREZ@bisuni.edu"), PASSWORD, new ProfileData.Admin(null), ADMIN));
        assertEquals("Ya existe un usuario con ese correo.", e1.getMessage());

        var e2 = assertThrows(ServiceException.class, () -> service.create(
                data("JPerez", "nuevo@bisuni.edu"), PASSWORD, new ProfileData.Admin(null), ADMIN));
        assertEquals("Ya existe un usuario con ese nombre de usuario.", e2.getMessage());
    }

    @Test
    void failedProfileRollsBackUser() throws Exception {
        int before = count(db, "SELECT count(*) FROM users");
        // La carrera 999 no existe: falla el perfil y no debe quedar el usuario suelto.
        assertThrows(ServiceException.class, () -> service.create(
                data("huerfano", "huerfano@bisuni.edu"), PASSWORD, new ProfileData.Student(999, 1), ADMIN));
        assertEquals(before, count(db, "SELECT count(*) FROM users"));
    }

    @Test
    void validationErrors() {
        assertThrows(ServiceException.class, () -> service.create(
                data("ab", "x@bisuni.edu"), PASSWORD, new ProfileData.Admin(null), ADMIN));
        assertThrows(ServiceException.class, () -> service.create(
                data("valido", "sin-arroba"), PASSWORD, new ProfileData.Admin(null), ADMIN));
        var shortPass = assertThrows(ServiceException.class, () -> service.create(
                data("valido", "valido@bisuni.edu"), "corta".toCharArray(), new ProfileData.Admin(null), ADMIN));
        assertTrue(shortPass.getMessage().contains("al menos 8"));
        assertThrows(ServiceException.class, () -> service.create(
                data("valido", "valido@bisuni.edu"), PASSWORD, new ProfileData.Teacher(null, "abc"), ADMIN));
        assertThrows(ServiceException.class, () -> service.create(
                data("valido", "valido@bisuni.edu"), PASSWORD, new ProfileData.Student(1, 13), ADMIN));
    }

    @Test
    void onlyAdminsCanCreate() {
        assertThrows(ServiceException.class, () -> service.create(
                data("valido", "valido@bisuni.edu"), PASSWORD, new ProfileData.Admin(null), TEACHER));
    }

    @Test
    void updateDataKeepsPasswordWhenEmpty() throws Exception {
        String hashBefore = text(db, "SELECT password_hash FROM users WHERE id_user = 3");
        service.update(3, new UserData("jperez", "Juan Carlos", "Pérez", "juan.perez@bisuni.edu",
                RecordStatus.Active), new char[0], ADMIN);
        assertEquals("Juan Carlos", text(db, "SELECT first_name FROM users WHERE id_user = 3"));
        assertEquals(hashBefore, text(db, "SELECT password_hash FROM users WHERE id_user = 3"));
    }

    @Test
    void updateCanResetPassword() throws Exception {
        service.update(3, new UserData("jperez", "Juan", "Pérez", "juan.perez@bisuni.edu", RecordStatus.Active),
                PASSWORD, ADMIN);
        auth.login("juan.perez@bisuni.edu", PASSWORD);
        assertThrows(ServiceException.class, () -> auth.login("juan.perez@bisuni.edu", "password123".toCharArray()));
    }

    @Test
    void adminCannotDeactivateSelf() {
        var e = assertThrows(ServiceException.class, () -> service.update(1,
                new UserData("admin.master", "Antonio", "Ramírez", "admin.master@bisuni.edu", RecordStatus.Inactive),
                null, ADMIN));
        assertEquals("No puedes desactivar tu propia cuenta.", e.getMessage());
    }

    @Test
    void deactivatedUserCannotLogIn() throws Exception {
        service.update(3, new UserData("jperez", "Juan", "Pérez", "juan.perez@bisuni.edu", RecordStatus.Inactive),
                null, ADMIN);
        var e = assertThrows(ServiceException.class, () -> auth.login("juan.perez@bisuni.edu", "password123".toCharArray()));
        assertEquals(AuthService.MSG_INACTIVE, e.getMessage());
    }

    @Test
    void updateDuplicateEmailIsRejected() {
        var e = assertThrows(ServiceException.class, () -> service.update(3,
                new UserData("jperez", "Juan", "Pérez", "laura.martinez@bisuni.edu", RecordStatus.Active), null, ADMIN));
        assertEquals("Ya existe un usuario con ese correo.", e.getMessage());
    }

    @Test
    void updateMissingUser() {
        var e = assertThrows(ServiceException.class, () -> service.update(99999,
                new UserData("nadie", "N", "N", "nadie@bisuni.edu", RecordStatus.Active), null, ADMIN));
        assertTrue(e.getMessage().contains("ya no existe"));
    }
}
