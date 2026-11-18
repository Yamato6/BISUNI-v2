package mx.bisuni.desktop.ui.shell;

import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import mx.bisuni.desktop.session.UserSession;
import mx.bisuni.desktop.ui.ChangePasswordDialog;
import mx.bisuni.desktop.ui.Dialogs;
import mx.bisuni.desktop.ui.Navigator;
import mx.bisuni.desktop.ui.admissions.AdmissionsController;
import mx.bisuni.desktop.ui.announcements.AnnouncementsController;
import mx.bisuni.desktop.ui.audit.AuditController;
import mx.bisuni.desktop.ui.dashboard.DashboardController;
import mx.bisuni.desktop.ui.teacher.AttendanceController;
import mx.bisuni.desktop.ui.careers.CareersController;
import mx.bisuni.desktop.ui.periods.PeriodsController;
import mx.bisuni.desktop.ui.groups.GroupsController;
import mx.bisuni.desktop.ui.schedule.ScheduleController;
import mx.bisuni.desktop.ui.students.StudentsController;
import mx.bisuni.desktop.ui.subjects.SubjectsController;
import mx.bisuni.desktop.model.TeacherClass;
import mx.bisuni.desktop.ui.teacher.GradesController;
import mx.bisuni.desktop.ui.teacher.MyClassesController;
import mx.bisuni.desktop.ui.teacher.MyGroupsController;
import mx.bisuni.desktop.ui.teacher.MyScheduleController;
import mx.bisuni.desktop.ui.teachers.TeachersController;
import mx.bisuni.desktop.ui.users.UsersController;

import java.util.function.Supplier;

/** Ventana principal: barra superior, menú lateral según el rol y área de contenido. */
public class ShellController {

    /** Una vista del área de contenido que puede pedir confirmación antes de cerrarse. */
    public interface ContentView {
        Node root();

        /** @return {@code false} para cancelar la navegación (p. ej. hay cambios sin guardar). */
        default boolean canLeave() {
            return true;
        }
    }

    @FXML private Label userNameLabel;
    @FXML private Label userRoleLabel;
    @FXML private VBox menu;
    @FXML private StackPane content;

    private final ToggleGroup menuGroup = new ToggleGroup();
    private Navigator navigator;
    private UserSession session;
    private ContentView current;

    public void init(Navigator navigator, UserSession session) {
        this.navigator = navigator;
        this.session = session;
        userNameLabel.setText(session.fullName());
        userRoleLabel.setText(session.role().label());

        // Evita que el grupo quede sin selección al volver a pulsar el botón activo.
        menuGroup.selectedToggleProperty().addListener((obs, old, sel) -> {
            if (sel == null && old != null) {
                old.setSelected(true);
            }
        });

        if (session.isAdmin()) {
            buildAdminMenu();
        } else {
            buildTeacherMenu();
        }
    }

    private void buildAdminMenu() {
        var ctx = navigator.context();
        ToggleButton home = addItem("Inicio", () -> {
            var view = Navigator.<DashboardController>load("ui/dashboard/dashboard.fxml");
            view.controller().init(ctx, session);
            return view.controller();
        });
        addItem("Admisiones", () -> {
            var view = Navigator.<AdmissionsController>load("ui/admissions/admissions.fxml");
            view.controller().init(ctx.admissions(), session);
            return view.controller();
        });
        addItem("Usuarios", () -> {
            var view = Navigator.<UsersController>load("ui/users/users.fxml");
            view.controller().init(ctx, session);
            return view.controller();
        });
        addItem("Alumnos", () -> {
            var view = Navigator.<StudentsController>load("ui/students/students.fxml");
            view.controller().init(ctx, session);
            return view.controller();
        });
        addItem("Profesores", () -> {
            var view = Navigator.<TeachersController>load("ui/teachers/teachers.fxml");
            view.controller().init(ctx, session);
            return view.controller();
        });
        addItem("Periodos", () -> {
            var view = Navigator.<PeriodsController>load("ui/periods/periods.fxml");
            view.controller().init(ctx, session);
            return view.controller();
        });
        addItem("Carreras", () -> {
            var view = Navigator.<CareersController>load("ui/careers/careers.fxml");
            view.controller().init(ctx, session);
            return view.controller();
        });
        addItem("Materias", () -> {
            var view = Navigator.<SubjectsController>load("ui/subjects/subjects.fxml");
            view.controller().init(ctx, session);
            return view.controller();
        });
        addItem("Grupos", () -> {
            var view = Navigator.<GroupsController>load("ui/groups/groups.fxml");
            view.controller().init(ctx, session);
            return view.controller();
        });
        addItem("Horarios", () -> {
            var view = Navigator.<ScheduleController>load("ui/schedule/schedule.fxml");
            view.controller().init(ctx, session);
            return view.controller();
        });
        addItem("Avisos", () -> announcements(ctx));
        addItem("Bitácora", () -> {
            var view = Navigator.<AuditController>load("ui/audit/audit.fxml");
            view.controller().init(ctx, session);
            return view.controller();
        });
        home.fire();
    }

    private void buildTeacherMenu() {
        var ctx = navigator.context();
        // "Mis clases" puede abrir Calificaciones con una clase ya elegida.
        TeacherClass[] pendingClass = new TeacherClass[1];
        ToggleButton[] gradesItem = new ToggleButton[1];

        ToggleButton classes = addItem("Mis clases", () -> {
            var view = Navigator.<MyClassesController>load("ui/teacher/my-classes.fxml");
            view.controller().init(ctx, session, cls -> {
                pendingClass[0] = cls;
                gradesItem[0].fire();
            });
            return view.controller();
        });
        addItem("Mi horario", () -> {
            var view = Navigator.<MyScheduleController>load("ui/teacher/my-schedule.fxml");
            view.controller().init(ctx, session);
            return view.controller();
        });
        addItem("Mis grupos", () -> {
            var view = Navigator.<MyGroupsController>load("ui/teacher/my-groups.fxml");
            view.controller().init(ctx, session);
            return view.controller();
        });
        gradesItem[0] = addItem("Calificaciones", () -> {
            var view = Navigator.<GradesController>load("ui/teacher/grades.fxml");
            view.controller().init(ctx, session, pendingClass[0]);
            pendingClass[0] = null;
            return view.controller();
        });
        addItem("Asistencia", () -> {
            var view = Navigator.<AttendanceController>load("ui/teacher/attendance.fxml");
            view.controller().init(ctx, session);
            return view.controller();
        });
        addItem("Avisos", () -> announcements(ctx));
        classes.fire();
    }

    private ContentView announcements(mx.bisuni.desktop.AppContext ctx) {
        var view = Navigator.<AnnouncementsController>load("ui/announcements/announcements.fxml");
        view.controller().init(ctx, session);
        return view.controller();
    }

    private ToggleButton addItem(String text, Supplier<ContentView> factory) {
        ToggleButton button = new ToggleButton(text);
        button.setToggleGroup(menuGroup);
        button.getStyleClass().add("menu-item");
        button.setMaxWidth(Double.MAX_VALUE);
        button.setAlignment(Pos.CENTER_LEFT);
        button.setOnAction(e -> {
            if (current != null && !current.canLeave()) {
                // Regresa la selección al botón anterior.
                menuGroup.getToggles().stream()
                        .filter(t -> t.getUserData() == current)
                        .findFirst()
                        .ifPresent(t -> t.setSelected(true));
                return;
            }
            current = factory.get();
            button.setUserData(current);
            content.getChildren().setAll(current.root());
        });
        menu.getChildren().add(button);
        return button;
    }

    @FXML
    private void onChangePassword() {
        ChangePasswordDialog.show(content, navigator.context().auth(), session, null).ifPresent(updated -> {
            session = updated;
            Dialogs.info(content, "Contraseña cambiada", "Tu contraseña se cambió.");
        });
    }

    @FXML
    private void onLogout() {
        if (current != null && !current.canLeave()) {
            return;
        }
        if (Dialogs.confirm(content, "Cerrar sesión", "¿Quieres cerrar sesión?", "Cerrar sesión")) {
            navigator.showLogin();
        }
    }
}
