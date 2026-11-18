package mx.bisuni.desktop.model;

import java.math.BigDecimal;
import java.util.List;

/**
 * Resumen del panel de inicio del admin.
 *
 * @param activePeriod       nombre del periodo activo; {@code null} si no hay
 * @param missingTeacher     "IT-2A · Web Development I": materias del periodo activo sin profesor
 * @param incompleteSchedule materias del periodo activo con menos horas en el horario que sus horas semanales
 */
public record Dashboard(
        String activePeriod,
        int pendingAspirants,
        int activeStudents,
        int groups,
        int openActas,
        List<CareerCount> studentsByCareer,
        List<FailingSubject> failing,
        List<String> missingTeacher,
        List<String> incompleteSchedule,
        List<RiskStudent> atRisk) {

    public record CareerCount(String career, int students) {
    }

    /** Materia de un grupo con alumnos reprobados (calificación final menor a 6). */
    public record FailingSubject(String subject, String group, String period, int failed, int graded) {
        public int pct() {
            return graded == 0 ? 0 : Math.round(100f * failed / graded);
        }
    }

    /** Alumno con más del 20 % de faltas en una materia del periodo activo. */
    public record RiskStudent(String name, String group, String subject, BigDecimal absencePct) {
    }
}
