package mx.bisuni.desktop.service;

import mx.bisuni.desktop.dao.UserDao;
import mx.bisuni.desktop.db.Database;
import mx.bisuni.desktop.model.Role;
import mx.bisuni.desktop.session.UserSession;
import mx.bisuni.desktop.support.PostgresTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthServiceTest {

    private static final char[] PASSWORD = "password123".toCharArray();

    private Database db;
    private AuthService auth;

    @BeforeEach
    void setUp() {
        db = PostgresTestSupport.freshDatabase();
        auth = new AuthService(db, new UserDao());
    }

    @Test
    void adminLogsInWithAdminProfileId() throws Exception {
        UserSession s = auth.login("admin.master@bisuni.edu", PASSWORD);
        assertEquals(Role.Admin, s.role());
        assertEquals(1, s.idUser());
        assertEquals(1, s.profileId(), "profileId debe ser id_admin");
        assertEquals("Antonio Ramírez", s.fullName());
    }

    @Test
    void adminLoginUpdatesLastAccess() throws Exception {
        OffsetDateTime before = lastAccess();
        Thread.sleep(20);
        auth.login("admin.master@bisuni.edu", PASSWORD);
        assertTrue(lastAccess().isAfter(before));
    }

    @Test
    void teacherLogsInWithTeacherProfileId() throws Exception {
        UserSession s = auth.login("juan.perez@bisuni.edu", PASSWORD);
        assertEquals(Role.Teacher, s.role());
        assertEquals(1, s.profileId(), "profileId debe ser id_teacher");
    }

    @Test
    void emailIsNormalized() throws Exception {
        UserSession s = auth.login("  Admin.MASTER@Bisuni.EDU ", PASSWORD);
        assertEquals("admin.master@bisuni.edu", s.email());
    }

    @Test
    void wrongPasswordAndUnknownEmailGiveSameMessage() {
        var wrongPass = assertThrows(ServiceException.class,
                () -> auth.login("admin.master@bisuni.edu", "nope".toCharArray()));
        var unknown = assertThrows(ServiceException.class,
                () -> auth.login("nadie@bisuni.edu", PASSWORD));
        assertEquals(AuthService.MSG_BAD_CREDENTIALS, wrongPass.getMessage());
        assertEquals(wrongPass.getMessage(), unknown.getMessage());
    }

    @Test
    void emptyInputIsRejected() {
        var e1 = assertThrows(ServiceException.class, () -> auth.login("  ", PASSWORD));
        var e2 = assertThrows(ServiceException.class, () -> auth.login("admin.master@bisuni.edu", new char[0]));
        assertEquals(AuthService.MSG_EMPTY, e1.getMessage());
        assertEquals(AuthService.MSG_EMPTY, e2.getMessage());
    }

    @Test
    void studentIsRejected() {
        var e = assertThrows(ServiceException.class, () -> auth.login("student001@bisuni.edu", PASSWORD));
        assertEquals(AuthService.MSG_NOT_STAFF, e.getMessage());
    }

    @Test
    void aspirantIsRejected() {
        var e = assertThrows(ServiceException.class, () -> auth.login("aspirant01@bisuni.edu", PASSWORD));
        assertEquals(AuthService.MSG_NOT_STAFF, e.getMessage());
    }

    @Test
    void inactiveUserIsRejected() throws Exception {
        db.withConnection(c -> c.createStatement()
                .executeUpdate("UPDATE users SET status = 'Inactive' WHERE email = 'juan.perez@bisuni.edu'"));
        var e = assertThrows(ServiceException.class, () -> auth.login("juan.perez@bisuni.edu", PASSWORD));
        assertEquals(AuthService.MSG_INACTIVE, e.getMessage());
    }

    private OffsetDateTime lastAccess() throws Exception {
        return db.withConnection(c -> {
            try (PreparedStatement ps = c.prepareStatement("SELECT last_access FROM admin_profile WHERE id_admin = 1");
                 ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getObject(1, OffsetDateTime.class);
            }
        });
    }
}
