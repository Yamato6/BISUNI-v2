package mx.bisuni.desktop.service;

import mx.bisuni.desktop.dao.StudentDao;
import mx.bisuni.desktop.dao.UserDao;
import mx.bisuni.desktop.db.Database;
import mx.bisuni.desktop.model.Role;
import mx.bisuni.desktop.model.UserData;
import mx.bisuni.desktop.session.UserSession;
import mx.bisuni.desktop.support.PostgresTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static mx.bisuni.desktop.support.Sql.count;
import static mx.bisuni.desktop.support.Sql.text;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Restablecimiento por el admin con contraseña temporal y cambio obligatorio al entrar. */
class PasswordResetTest {

    private static final UserSession ADMIN =
            new UserSession(1, "admin.master@bisuni.edu", "Antonio", "Ramírez", Role.Admin, 1);
    private static final String JUAN = "juan.perez@bisuni.edu";

    private Database db;
    private AuthService auth;
    private UserService users;

    @BeforeEach
    void setUp() {
        db = PostgresTestSupport.freshDatabase();
        auth = new AuthService(db, new UserDao());
        users = new UserService(db, new UserDao(), new StudentDao());
    }

    @Test
    void temporaryPasswordForcesAChange() throws Exception {
        String temp = users.resetPassword(3, ADMIN);
        assertEquals(10, temp.length());
        assertThrows(ServiceException.class, () -> auth.login(JUAN, "password123".toCharArray()),
                "la contraseña anterior deja de servir");

        UserSession s = auth.login(JUAN, temp.toCharArray());
        assertTrue(s.mustChangePassword());

        var wrong = assertThrows(ServiceException.class,
                () -> auth.changePassword(s, "otra".toCharArray(), "nueva-clave-1".toCharArray()));
        assertEquals("La contraseña actual no es correcta.", wrong.getMessage());
        assertThrows(ServiceException.class, () -> auth.changePassword(s, temp.toCharArray(), "corta".toCharArray()));
        assertThrows(ServiceException.class, () -> auth.changePassword(s, temp.toCharArray(), temp.toCharArray()));

        UserSession changed = auth.changePassword(s, temp.toCharArray(), "nueva-clave-1".toCharArray());
        assertFalse(changed.mustChangePassword());
        assertFalse(auth.login(JUAN, "nueva-clave-1".toCharArray()).mustChangePassword());
        assertEquals("false", text(db, "SELECT must_change_password::text FROM users WHERE id_user = 3"));
    }

    @Test
    void resetIsAuditedWithoutTheHash() throws Exception {
        users.resetPassword(13, ADMIN); // student001: en la web también se le pedirá cambiarla
        assertEquals("true", text(db, "SELECT must_change_password::text FROM users WHERE id_user = 13"));
        assertEquals("cambiada", text(db, """
                SELECT details ->> 'password_hash' FROM audit_log
                WHERE entity = 'users' AND entity_id = '13' AND actor_user = 1"""));
    }

    @Test
    void adminCannotResetTheirOwnOrUnknownAccounts() {
        assertThrows(ServiceException.class, () -> users.resetPassword(1, ADMIN));
        assertThrows(ServiceException.class, () -> users.resetPassword(9999, ADMIN));
        assertThrows(ServiceException.class, () -> users.resetPassword(13,
                new UserSession(3, JUAN, "Juan", "Pérez", Role.Teacher, 1)));
    }

    @Test
    void passwordSetByAnAdminMustAlsoBeChanged() throws Exception {
        users.update(3, new UserData("jperez", "Juan", "Pérez", JUAN, mx.bisuni.desktop.model.RecordStatus.Active),
                "puesta-por-admin".toCharArray(), ADMIN);
        assertTrue(auth.login(JUAN, "puesta-por-admin".toCharArray()).mustChangePassword());
        assertEquals(1, count(db, "SELECT count(*) FROM users WHERE must_change_password"));
    }
}
