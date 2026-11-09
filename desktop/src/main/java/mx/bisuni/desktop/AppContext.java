package mx.bisuni.desktop;

import mx.bisuni.desktop.config.AppConfig;
import mx.bisuni.desktop.config.ConfigException;
import mx.bisuni.desktop.dao.AdmissionDao;
import mx.bisuni.desktop.dao.AspirantDao;
import mx.bisuni.desktop.dao.CatalogDao;
import mx.bisuni.desktop.dao.StudentDao;
import mx.bisuni.desktop.dao.GroupDao;
import mx.bisuni.desktop.dao.ScheduleDao;
import mx.bisuni.desktop.dao.SubjectDao;
import mx.bisuni.desktop.dao.TeacherDao;
import mx.bisuni.desktop.dao.UserDao;
import mx.bisuni.desktop.db.Database;
import mx.bisuni.desktop.service.AdmissionService;
import mx.bisuni.desktop.service.AuthService;
import mx.bisuni.desktop.service.CatalogService;
import mx.bisuni.desktop.service.StudentService;
import mx.bisuni.desktop.service.GroupService;
import mx.bisuni.desktop.service.ScheduleService;
import mx.bisuni.desktop.service.SubjectService;
import mx.bisuni.desktop.service.TeacherService;
import mx.bisuni.desktop.service.UserService;

/**
 * Servicios compartidos de la app. Si la configuración falla, {@link #configError()} trae el
 * mensaje y los servicios quedan en {@code null}: el Login lo muestra en lugar de reventar.
 */
public record AppContext(
        Database db,
        AuthService auth,
        AdmissionService admissions,
        UserService users,
        StudentService students,
        TeacherService teachers,
        SubjectService subjects,
        GroupService groups,
        ScheduleService schedule,
        CatalogService catalog,
        String configError) {

    public static AppContext create() {
        try {
            return wire(Database.connect(AppConfig.load()));
        } catch (ConfigException e) {
            return new AppContext(null, null, null, null, null, null, null, null, null, null, e.getMessage());
        }
    }

    /** Arma todos los servicios sobre una base ya creada. */
    public static AppContext wire(Database db) {
        UserDao userDao = new UserDao();
        StudentDao studentDao = new StudentDao();
        SubjectDao subjectDao = new SubjectDao();
        GroupDao groupDao = new GroupDao();
        return new AppContext(
                db,
                new AuthService(db, userDao),
                new AdmissionService(db, new AspirantDao(), new AdmissionDao()),
                new UserService(db, userDao, studentDao),
                new StudentService(db, studentDao, userDao),
                new TeacherService(db, new TeacherDao(), userDao),
                new SubjectService(db, subjectDao),
                new GroupService(db, groupDao, subjectDao, studentDao),
                new ScheduleService(db, new ScheduleDao(), groupDao),
                new CatalogService(db, new CatalogDao()),
                null);
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
