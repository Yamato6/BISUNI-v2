package mx.bisuni.desktop.model;

import java.math.BigDecimal;

/**
 * Fila de la lista de alumnos.
 *
 * @param status         estatus de la cuenta de usuario (puede entrar o no)
 * @param academicStatus estatus académico (activo, baja, egresado)
 * @param gpa            promedio calculado de sus calificaciones finales; {@code null} si aún no tiene
 */
public record StudentRow(
        int idStudent,
        int idUser,
        String enrollmentNumber,
        String username,
        String firstName,
        String lastName,
        String email,
        RecordStatus status,
        StudentStatus academicStatus,
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
