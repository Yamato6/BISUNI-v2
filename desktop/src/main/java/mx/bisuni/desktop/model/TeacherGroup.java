package mx.bisuni.desktop.model;

/**
 * Grupo relacionado con el profesor: le da al menos una materia o es su tutor.
 *
 * @param mySubjects materias que el profesor imparte en el grupo, separadas por coma (vacío si solo es tutor)
 */
public record TeacherGroup(
        int idGroup,
        String name,
        String careerName,
        int semester,
        boolean tutor,
        String mySubjects,
        int studentCount) {
}
