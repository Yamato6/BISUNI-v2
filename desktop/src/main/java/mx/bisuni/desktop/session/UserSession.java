package mx.bisuni.desktop.session;

import mx.bisuni.desktop.model.Role;

/**
 * Sesión del usuario autenticado. Inmutable.
 *
 * @param profileId          {@code id_admin} si el rol es Admin, {@code id_teacher} si es Teacher.
 * @param mustChangePassword entró con una contraseña temporal: debe cambiarla antes de continuar.
 */
public record UserSession(int idUser, String email, String firstName, String lastName, Role role, int profileId,
                          boolean mustChangePassword) {

    public UserSession(int idUser, String email, String firstName, String lastName, Role role, int profileId) {
        this(idUser, email, firstName, lastName, role, profileId, false);
    }

    /** La misma sesión, ya con la contraseña cambiada. */
    public UserSession passwordChanged() {
        return new UserSession(idUser, email, firstName, lastName, role, profileId, false);
    }

    public String fullName() {
        return firstName + " " + lastName;
    }

    public boolean isAdmin() {
        return role == Role.Admin;
    }
}
