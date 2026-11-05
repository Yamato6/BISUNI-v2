package mx.bisuni.desktop.model;

import java.time.OffsetDateTime;

/** Fila de la lista de usuarios. */
public record UserRow(
        int idUser,
        String username,
        String firstName,
        String lastName,
        String email,
        Role role,
        RecordStatus status,
        OffsetDateTime createdAt) {

    public String fullName() {
        return firstName + " " + lastName;
    }

    public UserData data() {
        return new UserData(username, firstName, lastName, email, status);
    }
}
