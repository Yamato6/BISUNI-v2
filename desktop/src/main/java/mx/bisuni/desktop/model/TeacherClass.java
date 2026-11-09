package mx.bisuni.desktop.model;

/**
 * Una clase que imparte el profesor: una materia en un grupo ({@code group_subject} con su
 * {@code id_teacher}).
 *
 * @param gradedCount alumnos con los tres parciales capturados (tienen calificación final)
 */
public record TeacherClass(
        int idGroup,
        String groupName,
        String careerName,
        int semester,
        int idSubject,
        String subjectName,
        int weeklyHours,
        int studentCount,
        int scheduledHours,
        int gradedCount) {

    /** p. ej. "IT-1A · Programming Fundamentals". */
    public String label() {
        return groupName + " · " + subjectName;
    }

    @Override
    public String toString() {
        return label();
    }
}
