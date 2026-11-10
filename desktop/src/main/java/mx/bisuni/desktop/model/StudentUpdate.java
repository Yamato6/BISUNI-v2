package mx.bisuni.desktop.model;

/**
 * Cambios a un alumno: datos de usuario y de su perfil académico. El promedio ya no se edita:
 * se calcula de las calificaciones (vista {@code v_student_gpa}).
 */
public record StudentUpdate(UserData user, int idCareer, int semester, Integer idGroup) {
}
