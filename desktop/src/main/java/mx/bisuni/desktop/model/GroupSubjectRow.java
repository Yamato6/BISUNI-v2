package mx.bisuni.desktop.model;

/**
 * Materia que cursa un grupo ({@code group_subject}) con su profesor.
 *
 * @param scheduledHours clases de esta materia en el horario del grupo
 * @param gradeCount     calificaciones capturadas para esta materia en el grupo
 * @param actaClosed     el profesor cerró el acta
 */
public record GroupSubjectRow(
        int idSubject,
        String subjectName,
        int semester,
        int credits,
        int weeklyHours,
        Integer idTeacher,
        String teacherName,
        int scheduledHours,
        int gradeCount,
        boolean actaClosed) {

    public boolean hasTeacher() {
        return idTeacher != null;
    }
}
