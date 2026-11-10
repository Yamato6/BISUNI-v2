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
                    "El grupo seleccionado no pertenece a la carrera del alumno."),
            Map.entry("subjects_id_career_subject_name_key", "Ya existe una materia con ese nombre en la carrera."),
            Map.entry("school_groups_period_name", "Ya existe un grupo con ese nombre en el periodo."),
            Map.entry("academic_period_name_key", "Ya existe un periodo con ese nombre."),
            Map.entry("period_single_active", "Ya hay otro periodo activo."),
            Map.entry("period_dates", "La fecha de fin debe ser posterior a la de inicio."),
            Map.entry("period_closed_admissions", "Un periodo cerrado no puede tener la convocatoria abierta."),
            Map.entry("career_career_name_key", "Ya existe una carrera con ese nombre."),
            Map.entry("career_code_key", "Ya existe una carrera con esa clave."),
            Map.entry("career_code_format", "La clave de la carrera son de 2 a 4 letras mayúsculas (p. ej. IT)."),
            Map.entry("career_total_semesters", "El número de semestres debe estar entre 1 y 12."),
            Map.entry("student_profile_enrollment_number_key", "Esa matrícula ya está asignada; intenta de nuevo."),
            Map.entry("student_group_period_closed", "El grupo pertenece a un periodo cerrado; ya no se le asignan alumnos."),
            Map.entry("student_group_requires_active",
                    "Solo un alumno activo puede estar en un grupo. Quítalo del grupo antes de darlo de baja o egresarlo."),
            Map.entry("grades_period_not_active", "Las calificaciones solo se capturan en grupos del periodo activo."),
            Map.entry("grades_acta_closed", "El acta de esta materia ya está cerrada; no se pueden modificar calificaciones."),
            Map.entry("attendance_period_not_active", "La asistencia solo se registra en grupos del periodo activo."),
            Map.entry("attendance_date_out_of_period", "La fecha está fuera de las fechas del periodo."),
            Map.entry("announcement_audience_target", "Elige a quién va dirigido el aviso (carrera o grupo)."),
            Map.entry("announcement_expiry", "El vencimiento debe ser posterior a la publicación."),
            Map.entry("announcement_not_blank", "Escribe el título y el texto del aviso."),
            Map.entry("group_subject_pkey", "El grupo ya tiene esa materia."),
            Map.entry("grades_id_group_id_subject_fkey",
                    "La materia ya tiene calificaciones capturadas en este grupo; no se puede quitar."),
            Map.entry("schedule_group_clash", "El grupo ya tiene otra clase en ese día y bloque."),
            Map.entry("schedule_teacher_clash", "El profesor ya tiene otra clase en ese día y bloque."));

    private SqlErrors() {
    }

    /** Mensaje para la UI si la excepción es una violación conocida; si no, {@code fallback}. */
    static ServiceException translate(SQLException e, String fallback) {
        return translate(e, fallback, Map.of());
    }

    /**
     * Igual que {@link #translate(SQLException, String)}, pero {@code overrides} tiene prioridad:
     * la misma restricción puede significar cosas distintas según la operación que la dispara.
     */
    static ServiceException translate(SQLException e, String fallback, Map<String, String> overrides) {
        String constraint = constraintName(e);
        if (constraint != null && overrides.containsKey(constraint)) {
            return new ServiceException(overrides.get(constraint), e);
        }
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
