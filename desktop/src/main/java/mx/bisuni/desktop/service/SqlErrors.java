package mx.bisuni.desktop.service;

import org.postgresql.util.PSQLException;
import org.postgresql.util.ServerErrorMessage;

import java.sql.SQLException;
import java.util.Map;

/**
 * Traduce violaciones de restricciones de PostgreSQL a mensajes en español. Las restricciones
 * de la base son la última línea de defensa; aquí solo se les pone un mensaje entendible.
 */
final class SqlErrors {

    private static final String UNIQUE = "23505";
    private static final String FOREIGN_KEY = "23503";
    private static final String CHECK = "23514";

    /** Nombre de restricción (generado por Postgres o explícito en V1__schema.sql) → mensaje. */
    private static final Map<String, String> MESSAGES = Map.ofEntries(
            Map.entry("users_username_key", "Ya existe un usuario con ese nombre de usuario."),
            Map.entry("users_email_key", "Ya existe un usuario con ese correo."),
            Map.entry("users_email_lowercase", "El correo debe guardarse en minúsculas."),
            Map.entry("teacher_subject_pkey", "El profesor ya tiene asignada esa materia."),
            Map.entry("group_subject_id_teacher_id_subject_fkey",
                    "El profesor imparte esa materia en uno o más grupos. Primero asigna otro profesor a esos grupos."),
            Map.entry("student_profile_id_group_id_career_fkey",
                    "El grupo seleccionado no pertenece a la carrera del alumno."));

    private SqlErrors() {
    }

    /** Mensaje para la UI si la excepción es una violación conocida; si no, {@code fallback}. */
    static ServiceException translate(SQLException e, String fallback) {
        String constraint = constraintName(e);
        if (constraint != null && MESSAGES.containsKey(constraint)) {
            return new ServiceException(MESSAGES.get(constraint), e);
        }
        String state = e.getSQLState();
        if (UNIQUE.equals(state)) {
            return new ServiceException("Ya existe un registro con esos datos.", e);
        }
        if (FOREIGN_KEY.equals(state)) {
            return new ServiceException("El registro está en uso o hace referencia a datos que no existen.", e);
        }
        if (CHECK.equals(state)) {
            return new ServiceException("Algún dato está fuera del rango permitido.", e);
        }
        return new ServiceException(fallback, e);
    }

    private static String constraintName(SQLException e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof PSQLException pe) {
                ServerErrorMessage m = pe.getServerErrorMessage();
                if (m != null && m.getConstraint() != null) {
                    return m.getConstraint();
                }
            }
        }
        return null;
    }
}
