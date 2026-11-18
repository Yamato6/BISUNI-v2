package mx.bisuni.desktop;

import mx.bisuni.desktop.config.AppConfig;
import mx.bisuni.desktop.config.ConfigException;
import mx.bisuni.desktop.dao.AdmissionDao;
import mx.bisuni.desktop.dao.AnnouncementDao;
import mx.bisuni.desktop.dao.AttendanceDao;
import mx.bisuni.desktop.dao.AuditDao;
import mx.bisuni.desktop.dao.DashboardDao;
import mx.bisuni.desktop.dao.AspirantDao;
import mx.bisuni.desktop.dao.CareerDao;
import mx.bisuni.desktop.dao.CatalogDao;
import mx.bisuni.desktop.dao.StudentDao;
import mx.bisuni.desktop.dao.GradeDao;
import mx.bisuni.desktop.dao.GroupDao;
import mx.bisuni.desktop.dao.PeriodDao;
import mx.bisuni.desktop.dao.ScheduleDao;
import mx.bisuni.desktop.dao.SubjectDao;
import mx.bisuni.desktop.dao.TeacherDao;
import mx.bisuni.desktop.dao.TeachingDao;
import mx.bisuni.desktop.dao.UserDao;
import mx.bisuni.desktop.db.Database;
import mx.bisuni.desktop.service.AdmissionService;
import mx.bisuni.desktop.service.AnnouncementService;
import mx.bisuni.desktop.service.AttendanceService;
import mx.bisuni.desktop.service.AuditService;
import mx.bisuni.desktop.service.DashboardService;
import mx.bisuni.desktop.service.AuthService;
import mx.bisuni.desktop.service.CareerService;
import mx.bisuni.desktop.service.CatalogService;
import mx.bisuni.desktop.service.StudentService;
import mx.bisuni.desktop.service.GradeService;
import mx.bisuni.desktop.service.GroupService;
import mx.bisuni.desktop.service.PeriodService;
import mx.bisuni.desktop.service.ScheduleService;
import mx.bisuni.desktop.service.SubjectService;
import mx.bisuni.desktop.service.TeacherService;
import mx.bisuni.desktop.service.TeachingService;
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
        TeachingService teaching,
        GradeService grades,
        CatalogService catalog,
        PeriodService periods,
        CareerService careers,
        AttendanceService attendance,
        AnnouncementService announcements,
        AuditService audit,
        DashboardService dashboard,
        String configError) {

    public static AppContext create() {
        try {
            return wire(Database.connect(AppConfig.load()));
        } catch (ConfigException e) {
            return new AppContext(null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
                    e.getMessage());
        }
    }

    /** Arma todos los servicios sobre una base ya creada. */
    public static AppContext wire(Database db) {
        UserDao userDao = new UserDao();
        StudentDao studentDao = new StudentDao();
        SubjectDao subjectDao = new SubjectDao();
        GroupDao groupDao = new GroupDao();
        TeachingDao teachingDao = new TeachingDao();
        PeriodDao periodDao = new PeriodDao();
        return new AppContext(
                db,
                new AuthService(db, userDao),
                new AdmissionService(db, new AspirantDao(), new AdmissionDao()),
                new UserService(db, userDao, studentDao),
                new StudentService(db, studentDao, userDao),
                new TeacherService(db, new TeacherDao(), userDao),
                new SubjectService(db, subjectDao),
                new GroupService(db, groupDao, subjectDao, studentDao, periodDao),
                new ScheduleService(db, new ScheduleDao(), groupDao),
                new TeachingService(db, teachingDao, studentDao),
                new GradeService(db, new GradeDao(), teachingDao),
                new CatalogService(db, new CatalogDao()),
                new PeriodService(db, periodDao),
                new CareerService(db, new CareerDao()),
                new AttendanceService(db, new AttendanceDao(), teachingDao),
                new AnnouncementService(db, new AnnouncementDao(), teachingDao),
                new AuditService(db, new AuditDao()),
                new DashboardService(db, new DashboardDao()),
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
