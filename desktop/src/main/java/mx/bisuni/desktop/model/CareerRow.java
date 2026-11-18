package mx.bisuni.desktop.model;

/**
 * Fila del catálogo de carreras.
 *
 * @param code         clave corta que forma la matrícula (p. ej. IT en 2026IT0001)
 * @param maxSemester  semestre más alto entre sus materias (0 si no tiene)
 */
public record CareerRow(
        int idCareer,
        String name,
        String code,
        int totalSemesters,
        RecordStatus status,
        int studentCount,
        int subjectCount,
        int maxSemester) {

    public CareerData data() {
        return new CareerData(name, code, totalSemesters, status);
    }
}
