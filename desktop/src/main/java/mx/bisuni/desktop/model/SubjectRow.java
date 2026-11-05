package mx.bisuni.desktop.model;

/**
 * Fila de la lista de materias.
 *
 * @param groupCount   grupos que la cursan ({@code group_subject})
 * @param teacherCount profesores habilitados para impartirla ({@code teacher_subject})
 */
public record SubjectRow(
        int idSubject,
        String name,
        int idCareer,
        String careerName,
        int semester,
        int credits,
        int weeklyHours,
        RecordStatus status,
        int groupCount,
        int teacherCount) {

    public SubjectData data() {
        return new SubjectData(name, idCareer, semester, credits, weeklyHours, status);
    }
}
