package mx.bisuni.desktop.ui;

import javafx.application.Platform;
import javafx.scene.Parent;
import javafx.scene.control.TableView;
import mx.bisuni.desktop.AppContext;
import mx.bisuni.desktop.model.Role;
import mx.bisuni.desktop.session.UserSession;
import mx.bisuni.desktop.support.PostgresTestSupport;
import mx.bisuni.desktop.ui.admissions.AdmissionsController;
import mx.bisuni.desktop.ui.announcements.AnnouncementsController;
import mx.bisuni.desktop.ui.audit.AuditController;
import mx.bisuni.desktop.ui.dashboard.DashboardController;
import mx.bisuni.desktop.ui.teacher.AttendanceController;
import mx.bisuni.desktop.ui.careers.CareersController;
import mx.bisuni.desktop.ui.periods.PeriodsController;
import mx.bisuni.desktop.ui.login.LoginController;
import mx.bisuni.desktop.ui.groups.GroupsController;
import mx.bisuni.desktop.ui.schedule.ScheduleController;
import mx.bisuni.desktop.ui.students.StudentsController;
import mx.bisuni.desktop.ui.subjects.SubjectsController;
import mx.bisuni.desktop.ui.teacher.GradesController;
import mx.bisuni.desktop.ui.teacher.MyClassesController;
import mx.bisuni.desktop.ui.teacher.MyGroupsController;
import mx.bisuni.desktop.ui.teacher.MyScheduleController;
import mx.bisuni.desktop.ui.teachers.TeachersController;
import mx.bisuni.desktop.ui.users.UsersController;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Prueba de humo de las vistas: carga cada FXML con su controlador real contra una base de
 * prueba y espera a que la tabla se llene. Detecta fx:id mal escritos, bindings rotos y
 * consultas que fallan, cosas que la compilación no ve. Se omite si no hay JavaFX disponible.
 */
class ViewsSmokeTest {

    private static final UserSession ADMIN =
            new UserSession(1, "admin.master@bisuni.edu", "Antonio", "Ramírez", Role.Admin, 1);

    private AppContext ctx;

    @BeforeAll
    static void startFx() {
        try {
            Platform.startup(() -> { });
        } catch (IllegalStateException alreadyStarted) {
            // otra prueba ya lo inició
        } catch (Throwable t) {
            assumeTrue(false, "JavaFX no disponible: " + t);
        }
        Platform.setImplicitExit(false);
    }

    @BeforeEach
    void setUp() {
        ctx = AppContext.wire(PostgresTestSupport.freshDatabase());
    }

    @Test
    void loginLoads() throws Exception {
        Parent root = onFx(() -> {
            var v = Navigator.<LoginController>load("ui/login/login.fxml");
            assertNotNull(v.controller());
            return v.root();
        });
        assertNotNull(root.lookup("#emailField"));
    }

    @Test
    void admissionsLoadsRows() throws Exception {
        assertTableFills("ui/admissions/admissions.fxml",
                (AdmissionsController c, AppContext ctx) -> c.init(ctx.admissions(), ADMIN));
    }

    @Test
    void usersLoadsRows() throws Exception {
        assertTableFills("ui/users/users.fxml", (UsersController c, AppContext ctx) -> c.init(ctx, ADMIN));
    }

    @Test
    void studentsLoadsRows() throws Exception {
        assertTableFills("ui/students/students.fxml", (StudentsController c, AppContext ctx) -> c.init(ctx, ADMIN));
    }

    @Test
    void teachersLoadsRows() throws Exception {
        assertTableFills("ui/teachers/teachers.fxml", (TeachersController c, AppContext ctx) -> c.init(ctx, ADMIN));
    }

    @Test
    void subjectsLoadsRows() throws Exception {
        assertTableFills("ui/subjects/subjects.fxml", (SubjectsController c, AppContext ctx) -> c.init(ctx, ADMIN));
    }

    @Test
    void periodsLoadsRows() throws Exception {
        assertTableFills("ui/periods/periods.fxml", (PeriodsController c, AppContext ctx) -> c.init(ctx, ADMIN));
    }

    @Test
    void careersLoadsRows() throws Exception {
        assertTableFills("ui/careers/careers.fxml", (CareersController c, AppContext ctx) -> c.init(ctx, ADMIN));
    }

    @Test
    void groupsLoadsRows() throws Exception {
        assertTableFills("ui/groups/groups.fxml", (GroupsController c, AppContext ctx) -> c.init(ctx, ADMIN));
    }

    @Test
    void scheduleShowsFirstGroup() throws Exception {
        ScheduleController controller = onFx(() -> {
            Navigator.View<ScheduleController> v = Navigator.load("ui/schedule/schedule.fxml");
            v.controller().init(ctx, ADMIN);
            return v.controller();
        });
        long deadline = System.currentTimeMillis() + 15_000;
        int shown = 0;
        while (System.currentTimeMillis() < deadline) {
            shown = onFx(controller::shownEntries);
            if (shown > 0) {
                break;
            }
            Thread.sleep(100);
        }
        assertEquals(9, shown, "IT-1A tiene 9 clases en el seed (5 + 4)");
    }

    private static final UserSession JUAN =
            new UserSession(3, "juan.perez@bisuni.edu", "Juan", "Pérez", Role.Teacher, 1);

    @Test
    void teacherClassesAndGroupsLoad() throws Exception {
        assertTableFills("ui/teacher/my-classes.fxml",
                (MyClassesController c, AppContext ctx) -> c.init(ctx, JUAN, cls -> { }));
        assertTableFills("ui/teacher/my-groups.fxml", (MyGroupsController c, AppContext ctx) -> c.init(ctx, JUAN));
    }

    @Test
    void teacherScheduleShowsClasses() throws Exception {
        MyScheduleController controller = onFx(() -> {
            Navigator.View<MyScheduleController> v = Navigator.load("ui/teacher/my-schedule.fxml");
            v.controller().init(ctx, JUAN);
            return v.controller();
        });
        assertEquals(10, waitFor(controller::shownEntries), "Juan da 5 clases en IT-1A y 5 en IT-1B");
    }

    @Test
    void gradesSheetLoadsFirstClass() throws Exception {
        GradesController controller = onFx(() -> {
            Navigator.View<GradesController> v = Navigator.load("ui/teacher/grades.fxml");
            v.controller().init(ctx, JUAN, null);
            return v.controller();
        });
        assertTrue(waitFor(controller::shownRows) > 0);
    }

    @Test
    void attendanceLoadsFirstClass() throws Exception {
        AttendanceController controller = onFx(() -> {
            Navigator.View<AttendanceController> v = Navigator.load("ui/teacher/attendance.fxml");
            v.controller().init(ctx, JUAN);
            return v.controller();
        });
        assertEquals(10, waitFor(controller::shownRows), "IT-1A tiene 10 alumnos");
    }

    @Test
    void announcementsLoadForAdminAndTeacher() throws Exception {
        assertTableFills("ui/announcements/announcements.fxml",
                (AnnouncementsController c, AppContext ctx) -> c.init(ctx, ADMIN));
        assertTableFills("ui/announcements/announcements.fxml",
                (AnnouncementsController c, AppContext ctx) -> c.init(ctx, JUAN));
    }

    @Test
    void auditShowsChanges() throws Exception {
        ctx.users().resetPassword(13, ADMIN); // el seed no deja rastro: se genera un cambio
        assertTableFills("ui/audit/audit.fxml", (AuditController c, AppContext ctx) -> c.init(ctx, ADMIN));
    }

    @Test
    void dashboardLoads() throws Exception {
        DashboardController controller = onFx(() -> {
            Navigator.View<DashboardController> v = Navigator.load("ui/dashboard/dashboard.fxml");
            v.controller().init(ctx, ADMIN);
            return v.controller();
        });
        assertEquals(1, waitFor(() -> controller.isLoaded() ? 1 : 0));
    }

    /** Espera (hasta 15 s) a que {@code probe}, leído en el hilo FX, sea mayor que cero. */
    private static int waitFor(Supplier<Integer> probe) throws Exception {
        long deadline = System.currentTimeMillis() + 15_000;
        int n = 0;
        while (System.currentTimeMillis() < deadline) {
            n = onFx(probe);
            if (n > 0) {
                break;
            }
            Thread.sleep(100);
        }
        return n;
    }

    /**
     * La tabla con fx:id=table. {@code lookup} no entra a los elementos de un SplitPane sin skin
     * (la vista no está en una escena), así que en ese caso se lee el campo del controlador.
     */
    private static TableView<?> findTable(Navigator.View<?> view) throws Exception {
        if (view.root().lookup("#table") instanceof TableView<?> t) {
            return t;
        }
        var field = view.controller().getClass().getDeclaredField("table");
        field.setAccessible(true);
        return (TableView<?>) field.get(view.controller());
    }

    private <C> void assertTableFills(String fxml, BiConsumer<C, AppContext> init) throws Exception {
        Navigator.View<C> view = onFx(() -> {
            Navigator.View<C> v = Navigator.load(fxml);
            init.accept(v.controller(), ctx);
            return v;
        });
        TableView<?> table = findTable(view);
        assertNotNull(table, "la vista debe tener una tabla con fx:id=table");
        long deadline = System.currentTimeMillis() + 15_000;
        int size = 0;
        while (System.currentTimeMillis() < deadline) {
            size = onFx(() -> table.getItems().size());
            if (size > 0) {
                break;
            }
            Thread.sleep(100);
        }
        assertTrue(size > 0, fxml + ": la tabla no se llenó");
    }

    private static <T> T onFx(Supplier<T> work) throws Exception {
        CompletableFuture<T> f = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                f.complete(work.get());
            } catch (Throwable t) {
                f.completeExceptionally(t);
            }
        });
        return f.get(15, TimeUnit.SECONDS);
    }
}
