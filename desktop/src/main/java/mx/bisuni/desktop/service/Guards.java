package mx.bisuni.desktop.service;

import mx.bisuni.desktop.session.UserSession;

/** Comprobaciones de permisos comunes a los servicios. */
final class Guards {
    private Guards() {
    }

    static void requireAdmin(UserSession session) throws ServiceException {
        if (session == null || !session.isAdmin()) {
            throw new ServiceException("Solo un administrador puede realizar esta acción.");
        }
    }
}
