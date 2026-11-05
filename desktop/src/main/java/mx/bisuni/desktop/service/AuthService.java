package mx.bisuni.desktop.service;

import at.favre.lib.crypto.bcrypt.BCrypt;
import mx.bisuni.desktop.dao.UserDao;
import mx.bisuni.desktop.db.Database;
import mx.bisuni.desktop.model.Role;
import mx.bisuni.desktop.session.UserSession;

import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.Locale;
import java.util.Optional;

public final class AuthService {

    static final String MSG_EMPTY = "Escribe tu correo y tu contraseña.";
    static final String MSG_BAD_CREDENTIALS = "Correo o contraseña incorrectos.";
    static final String MSG_INACTIVE = "Tu cuenta está desactivada. Contacta a control escolar.";
    static final String MSG_NOT_STAFF = "Esta aplicación es solo para administradores y profesores.";
    static final String MSG_NO_PROFILE = "Tu cuenta no tiene un perfil de personal asignado. Contacta a control escolar.";
    static final String MSG_DB = "No se pudo conectar con la base de datos.";

    /**
     * Hash bcrypt de relleno: si el email no existe se verifica contra él para que la respuesta
     * tarde lo mismo que con una contraseña incorrecta y no se pueda saber qué correos existen.
     */
    private static final byte[] DUMMY_HASH =
            "$2b$10$dBVhXUfjbLZ./s1iqnh7Z.QTdJWw1Umm1yoO2LWON8mQ71.PIQvSO".getBytes(StandardCharsets.UTF_8);

    private final Database db;
    private final UserDao users;

    public AuthService(Database db, UserDao users) {
        this.db = db;
        this.users = users;
    }

    public static String normalizeEmail(String email) {
        return email == null ? "" : email.strip().toLowerCase(Locale.ROOT);
    }

    public UserSession login(String email, char[] password) throws ServiceException {
        String normalized = normalizeEmail(email);
        if (normalized.isEmpty() || password == null || password.length == 0) {
            throw new ServiceException(MSG_EMPTY);
        }
        try {
            Optional<UserDao.Credentials> found = db.withConnection(c -> users.findByEmail(c, normalized));
            byte[] hash = found.map(u -> u.passwordHash().getBytes(StandardCharsets.UTF_8)).orElse(DUMMY_HASH);
            boolean ok = BCrypt.verifyer().verify(password, hash).verified;
            if (found.isEmpty() || !ok) {
                throw new ServiceException(MSG_BAD_CREDENTIALS);
            }
            UserDao.Credentials u = found.get();
            if (!u.active()) {
                throw new ServiceException(MSG_INACTIVE);
            }
            if (!u.role().isStaff()) {
                throw new ServiceException(MSG_NOT_STAFF);
            }
            Integer profileId = u.role() == Role.Admin ? u.idAdmin() : u.idTeacher();
            if (profileId == null) {
                throw new ServiceException(MSG_NO_PROFILE);
            }
            if (u.role() == Role.Admin) {
                db.withConnection(c -> {
                    users.touchAdminLastAccess(c, profileId);
                    return null;
                });
            }
            return new UserSession(u.idUser(), u.email(), u.firstName(), u.lastName(), u.role(), profileId);
        } catch (SQLException e) {
            throw new ServiceException(MSG_DB, e);
        }
    }
}
