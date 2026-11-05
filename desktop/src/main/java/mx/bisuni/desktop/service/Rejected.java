package mx.bisuni.desktop.service;

/**
 * Rechazo de una regla de negocio dentro de {@code Database.inTransaction}: al ser unchecked
 * provoca el rollback, y el servicio la convierte en {@link ServiceException} con el mismo mensaje.
 */
final class Rejected extends RuntimeException {
    Rejected(String message) {
        super(message, null, false, false);
    }
}
