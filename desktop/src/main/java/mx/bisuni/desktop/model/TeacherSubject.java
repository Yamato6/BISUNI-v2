package mx.bisuni.desktop.model;

/**
 * Materia que un profesor está habilitado para impartir ({@code teacher_subject}).
 *
 * @param groups grupos donde actualmente la imparte ({@code group_subject.id_teacher}), separados por coma;
 *               vacío si no la imparte en ninguno.
 */
public record TeacherSubject(int idSubject, String subjectName, String careerName, int semester, int credits,
                             String groups) {

    public boolean isAssignedToGroups() {
        return groups != null && !groups.isEmpty();
    }
}
