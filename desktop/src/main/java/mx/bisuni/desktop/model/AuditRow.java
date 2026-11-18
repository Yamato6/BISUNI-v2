package mx.bisuni.desktop.model;

import java.time.OffsetDateTime;

/**
 * Renglón de la bitácora ({@code audit_log}).
 *
 * @param actorName quién hizo el cambio; {@code null} si fue sin sesión (p. ej. SQL directo)
 * @param subject   descripción legible del registro afectado (alumno, grupo, materia…)
 * @param details   un cambio por línea: {@code campo|antes|después} (vacío = sin valor); {@code null} sin cambios
 */
public record AuditRow(
        long idAudit,
        OffsetDateTime at,
        String actorName,
        String action,
        String entity,
        String entityId,
        String subject,
        String details) {
}
