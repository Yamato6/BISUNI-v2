package mx.bisuni.desktop.ui;

import javafx.application.Platform;
import javafx.scene.Parent;
import javafx.scene.control.TableView;
import mx.bisuni.desktop.AppContext;
import mx.bisuni.desktop.model.Role;
import mx.bisuni.desktop.session.UserSession;
import mx.bisuni.desktop.support.PostgresTestSupport;
import mx.bisuni.desktop.ui.admissions.AdmissionsController;
import mx.bisuni.desktop.ui.login.LoginController;
import mx.bisuni.desktop.ui.students.StudentsController;
import mx.bisuni.desktop.ui.teachers.TeachersController;
import mx.bisuni.desktop.ui.users.UsersController;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

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

    private <C> void assertTableFills(String fxml, BiConsumer<C, AppContext> init) throws Exception {
        Parent root = onFx(() -> {
            Navigator.View<C> v = Navigator.load(fxml);
            init.accept(v.controller(), ctx);
            return v.root();
        });
        TableView<?> table = (TableView<?>) root.lookup("#table");
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
