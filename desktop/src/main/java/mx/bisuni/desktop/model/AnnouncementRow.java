package mx.bisuni.desktop.model;

import java.time.OffsetDateTime;

/**
 * Aviso publicado.
 *
 * @param target nombre de la carrera o del grupo (con su periodo); {@code null} si es para todos
 */
public record AnnouncementRow(
        int idAnnouncement,
        String title,
        String body,
        AnnouncementAudience audience,
        String target,
        int authorUser,
        String authorName,
        OffsetDateTime publishedAt,
        OffsetDateTime expiresAt) {

    public boolean expired() {
        return expiresAt != null && !expiresAt.isAfter(OffsetDateTime.now());
    }

    /** "Toda la escuela", "Carrera: Information Technology" o "Grupo: IT-1A (2026-2)". */
    public String audienceText() {
        return switch (audience) {
            case All -> audience.label();
            case Career -> "Carrera: " + target;
            case Group -> "Grupo: " + target;
        };
    }
}
