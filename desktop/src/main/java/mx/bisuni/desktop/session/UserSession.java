package mx.bisuni.desktop.session;

import mx.bisuni.desktop.model.Role;

/**
 * Sesión del usuario autenticado. Inmutable.
 *
 * @param profileId {@code id_admin} si el rol es Admin, {@code id_teacher} si es Teacher.
 */
public record UserSession(int idUser, String email, String firstName, String lastName, Role role, int profileId) {

    public String fullName() {
        return firstName + " " + lastName;
    }

    public boolean isAdmin() {
        return role == Role.Admin;
    }
}
