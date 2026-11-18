package mx.bisuni.desktop.model;

import java.time.LocalDate;

/**
 * Aviso nuevo. Según {@code audience} se usa {@code idCareer} o {@code idGroup}.
 *
 * @param expiresOn último día en que se muestra; {@code null} = no vence
 */
public record AnnouncementData(String title, String body, AnnouncementAudience audience, Integer idCareer,
                               Integer idGroup, LocalDate expiresOn) {
}
