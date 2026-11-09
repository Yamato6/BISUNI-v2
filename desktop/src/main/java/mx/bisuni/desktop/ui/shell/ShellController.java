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
import mx.bisuni.desktop.ui.Dialogs;
import mx.bisuni.desktop.ui.Navigator;
import mx.bisuni.desktop.ui.admissions.AdmissionsController;
import mx.bisuni.desktop.ui.groups.GroupsController;
import mx.bisuni.desktop.ui.schedule.ScheduleController;
import mx.bisuni.desktop.ui.students.StudentsController;
import mx.bisuni.desktop.ui.subjects.SubjectsController;
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
        ToggleButton admissions = addItem("Admisiones", () -> {
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
        admissions.fire();
    }

    private void buildTeacherMenu() {
        ToggleButton home = addItem("Inicio", () -> simpleView(
                "Bienvenido, " + session.firstName(),
                "Desde el menú podrás consultar tus materias, tu horario y tus grupos, y capturar calificaciones."));
        addSoon("Mis materias");
        addSoon("Mi horario");
        addSoon("Mis grupos");
        addSoon("Calificaciones");
        home.fire();
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

    private void addSoon(String text) {
        addItem(text, () -> simpleView(text, "Esta sección estará disponible próximamente."));
    }

    private static ContentView simpleView(String title, String message) {
        Label t = new Label(title);
        t.getStyleClass().add("view-title");
        Label m = new Label(message);
        m.setWrapText(true);
        VBox box = new VBox(10, t, m);
        box.getStyleClass().add("view");
        return () -> box;
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
