package mx.bisuni.desktop;

import mx.bisuni.desktop.config.AppConfig;
import mx.bisuni.desktop.config.ConfigException;
import mx.bisuni.desktop.dao.AdmissionDao;
import mx.bisuni.desktop.dao.AspirantDao;
import mx.bisuni.desktop.dao.UserDao;
import mx.bisuni.desktop.db.Database;
import mx.bisuni.desktop.service.AdmissionService;
import mx.bisuni.desktop.service.AuthService;

/**
 * Servicios compartidos de la app. Si la configuración falla, {@link #configError()} trae el
 * mensaje y los servicios quedan en {@code null}: el Login lo muestra en lugar de reventar.
 */
public record AppContext(Database db, AuthService auth, AdmissionService admissions, String configError) {

    public static AppContext create() {
        try {
            Database db = Database.connect(AppConfig.load());
            return new AppContext(
                    db,
                    new AuthService(db, new UserDao()),
                    new AdmissionService(db, new AspirantDao(), new AdmissionDao()),
                    null);
        } catch (ConfigException e) {
            return new AppContext(null, null, null, e.getMessage());
        }
    }

    public boolean isConfigured() {
        return configError == null;
    }

    public void close() {
        if (db != null) {
            db.close();
        }
    }
}
