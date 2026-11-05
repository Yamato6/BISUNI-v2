package mx.bisuni.desktop.model;

import java.math.BigDecimal;

/** Cambios a un alumno: datos de usuario y de su perfil académico. */
public record StudentUpdate(UserData user, int idCareer, int semester, BigDecimal gpa, Integer idGroup) {
}
