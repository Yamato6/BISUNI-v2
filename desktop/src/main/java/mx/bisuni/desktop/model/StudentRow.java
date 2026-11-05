package mx.bisuni.desktop.model;

import java.math.BigDecimal;

/** Fila de la lista de alumnos. */
public record StudentRow(
        int idStudent,
        int idUser,
        String username,
        String firstName,
        String lastName,
        String email,
        RecordStatus status,
        int idCareer,
        String careerName,
        int semester,
        BigDecimal gpa,
        Integer idGroup,
        String groupName) {

    public UserData userData() {
        return new UserData(username, firstName, lastName, email, status);
    }
}
