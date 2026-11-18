package mx.bisuni.desktop.model;

import java.math.BigDecimal;

/**
 * Renglón de la lista de asistencia de una clase en una fecha.
 *
 * @param status     marca de esa fecha; {@code null} si todavía no se pasó lista
 * @param sessions   sesiones registradas del alumno en la clase (todo el periodo)
 * @param absences   faltas en esas sesiones
 * @param absencePct porcentaje de faltas; {@code null} sin sesiones
 */
public record AttendanceRow(
        int idStudent,
        String lastName,
        String firstName,
        String enrollmentNumber,
        AttendanceStatus status,
        int sessions,
        int absences,
        BigDecimal absencePct) {

    public String fullName() {
        return lastName + ", " + firstName;
    }
}
