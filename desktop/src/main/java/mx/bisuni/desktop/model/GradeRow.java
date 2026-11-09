package mx.bisuni.desktop.model;

import java.math.BigDecimal;

/**
 * Renglón de la hoja de calificaciones de una clase.
 *
 * @param inGroup {@code false} si el alumno ya no está en el grupo pero tiene calificaciones de
 *                esta clase (se conservan y se pueden seguir editando)
 * @param finalGrade calculada por la base (promedio de los 3 parciales); {@code null} si falta alguno
 */
public record GradeRow(
        int idStudent,
        String lastName,
        String firstName,
        String username,
        boolean inGroup,
        BigDecimal parcial1,
        BigDecimal parcial2,
        BigDecimal parcial3,
        BigDecimal finalGrade) {

    public String fullName() {
        return lastName + ", " + firstName;
    }
}
