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

import java.sql.SQLException;
import java.util.List;

/**
 * Alta y edición de usuarios. A diferencia de la v1:
 * <ul>
 *   <li>El rol se fija al crear y no se cambia después (en la v1 se podía cambiar sin migrar perfiles).</li>
 *   <li>El usuario y su perfil se crean en una sola transacción.</li>
 *   <li>Los aspirantes no se crean aquí: se registran desde la web con su CURP.</li>
 * </ul>
 */
public final class UserService {

    private final Database db;
    private final UserDao users;
    private final StudentDao students;

    public UserService(Database db, UserDao users, StudentDao students) {
        this.db = db;
        this.users = users;
        this.students = students;
    }

    public List<UserRow> list(Role role, RecordStatus status, String search) throws ServiceException {
        try {
            return db.withConnection(c -> users.list(c, role, status, search));
        } catch (SQLException e) {
            throw new ServiceException("No se pudo cargar la lista de usuarios.", e);
        }
    }

    /** Crea el usuario con su perfil. Devuelve el {@code id_user} nuevo. */
    public int create(UserData data, char[] password, ProfileData profile, UserSession admin) throws ServiceException {
        Guards.requireAdmin(admin);
        UserData d = Validation.user(data);
        Passwords.validate(password);
        if (profile == null) {
            throw new ServiceException("Selecciona el rol.");
        }
        ProfileData p = validate(profile);
        String hash = Passwords.hash(password);
        try {
            return db.actor(admin.idUser()).inTransaction(c -> {
                int idUser = users.insert(c, d, p.role(), hash);
                switch (p) {
                    case ProfileData.Admin a -> users.insertAdminProfile(c, idUser, a.position());
                    case ProfileData.Teacher t -> users.insertTeacherProfile(c, idUser, t.specialty(), t.phone());
                    case ProfileData.Student s -> students.insert(c, idUser, s.idCareer(), s.semester());
                }
                return idUser;
            });
        } catch (SQLException e) {
            throw SqlErrors.translate(e, "No se pudo crear el usuario.");
        }
    }

    /**
     * Actualiza los datos del usuario y, si {@code newPassword} trae algo, también su contraseña.
     */
    public void update(int idUser, UserData data, char[] newPassword, UserSession admin) throws ServiceException {
        Guards.requireAdmin(admin);
        UserData d = Validation.user(data);
        boolean changePassword = newPassword != null && newPassword.length > 0;
        if (changePassword) {
            Passwords.validate(newPassword);
        }
        checkNotDeactivatingSelf(idUser, d, admin);
        String hash = changePassword ? Passwords.hash(newPassword) : null;
        try {
            db.actor(admin.idUser()).inTransaction(c -> {
                if (!users.update(c, idUser, d)) {
                    throw new SQLException("El usuario ya no existe", "02000");
                }
                if (hash != null) {
                    // La puso el admin: el usuario debe cambiarla al entrar.
                    users.updatePassword(c, idUser, hash, true);
                }
                return null;
            });
        } catch (SQLException e) {
            if ("02000".equals(e.getSQLState())) {
                throw new ServiceException("El usuario ya no existe. Recarga la lista.", e);
            }
            throw SqlErrors.translate(e, "No se pudo guardar el usuario.");
        }
    }

    /**
     * Restablece la contraseña con una temporal aleatoria y obliga a cambiarla al entrar (escritorio
     * o web). Devuelve la temporal para que el admin se la entregue al usuario; no se guarda en claro.
     */
    public String resetPassword(int idUser, UserSession admin) throws ServiceException {
        Guards.requireAdmin(admin);
        if (idUser == admin.idUser()) {
            throw new ServiceException("Para tu propia cuenta usa «Cambiar contraseña».");
        }
        char[] temp = Passwords.temporary();
        String hash = Passwords.hash(temp);
        boolean found;
        try {
            found = db.actor(admin.idUser()).inTransaction(c -> users.updatePassword(c, idUser, hash, true));
        } catch (SQLException e) {
            throw SqlErrors.translate(e, "No se pudo restablecer la contraseña.");
        }
        if (!found) {
            throw new ServiceException("El usuario ya no existe. Recarga la lista.");
        }
        return new String(temp);
    }

    static void checkNotDeactivatingSelf(int idUser, UserData d, UserSession admin) throws ServiceException {
        if (idUser == admin.idUser() && d.status() == RecordStatus.Inactive) {
            throw new ServiceException("No puedes desactivar tu propia cuenta.");
        }
    }

    private static ProfileData validate(ProfileData p) throws ServiceException {
        return switch (p) {
            case ProfileData.Admin a -> new ProfileData.Admin(Validation.optional(a.position(), "el puesto", 50));
            case ProfileData.Teacher t -> new ProfileData.Teacher(
                    Validation.optional(t.specialty(), "la especialidad", 100), Validation.phone(t.phone()));
            case ProfileData.Student s -> {
                if (s.idCareer() <= 0) {
                    throw new ServiceException("Selecciona la carrera del alumno.");
                }
                yield new ProfileData.Student(s.idCareer(), Validation.semester(s.semester()));
            }
        };
    }
}
