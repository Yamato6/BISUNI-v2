package mx.bisuni.desktop.model;

/**
 * Fila de la lista de grupos.
 *
 * @param unassignedCount materias del grupo que todavía no tienen profesor asignado
 */
public record GroupRow(
        int idGroup,
        String name,
        int idCareer,
        String careerName,
        int semester,
        Integer idTutor,
        String tutorName,
        RecordStatus status,
        int studentCount,
        int subjectCount,
        int unassignedCount) {

    public GroupData data() {
        return new GroupData(name, idCareer, semester, idTutor, status);
    }
}
