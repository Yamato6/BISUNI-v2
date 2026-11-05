package mx.bisuni.desktop.ui.teachers;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import mx.bisuni.desktop.AppContext;
import mx.bisuni.desktop.model.Catalog;
import mx.bisuni.desktop.model.TeacherRow;
import mx.bisuni.desktop.model.TeacherSubject;
import mx.bisuni.desktop.session.UserSession;
import mx.bisuni.desktop.ui.Async;
import mx.bisuni.desktop.ui.Dialogs;
import mx.bisuni.desktop.ui.ListSupport;
import mx.bisuni.desktop.ui.Navigator;

import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

/**
 * Materias que un profesor puede impartir ({@code teacher_subject}). Cada alta o baja se guarda
 * al momento. Una materia que el profesor imparte en algún grupo no se puede quitar hasta que
 * ese grupo tenga otro profesor asignado.
 */
final class TeacherSubjectsDialog {

    private final AppContext ctx;
    private final UserSession session;
    private final TeacherRow teacher;

    private final Dialog<ButtonType> dialog = new Dialog<>();
    private final TableView<TeacherSubject> table = new TableView<>();
    private final ComboBox<Catalog.Subject> available = new ComboBox<>();
    private final Button addButton = new Button("Agregar");
    private final Button removeButton = new Button("Quitar materia");
    private final Label status = new Label();
    private List<Catalog.Subject> catalog = List.of();

    private TeacherSubjectsDialog(Node owner, AppContext ctx, UserSession session, TeacherRow teacher) {
        this.ctx = ctx;
        this.session = session;
        this.teacher = teacher;
        dialog.setTitle("Materias de " + teacher.fullName());
        dialog.setHeaderText(null);
        dialog.setResizable(true);
        if (owner != null && owner.getScene() != null) {
            dialog.initOwner(owner.getScene().getWindow());
        }
        dialog.getDialogPane().getButtonTypes().add(new ButtonType("Cerrar", ButtonBar.ButtonData.CANCEL_CLOSE));
        dialog.getDialogPane().getStylesheets().add(Navigator.resource("css/bisuni.css").toExternalForm());
        dialog.getDialogPane().setContent(buildContent());
        dialog.getDialogPane().setPrefSize(820, 520);
    }

    static void show(Node owner, AppContext ctx, UserSession session, TeacherRow teacher, Runnable onClose) {
        TeacherSubjectsDialog d = new TeacherSubjectsDialog(owner, ctx, session, teacher);
        d.reload(true);
        d.dialog.showAndWait();
        onClose.run();
    }

    private VBox buildContent() {
        TableColumn<TeacherSubject, String> name = new TableColumn<>("Materia");
        TableColumn<TeacherSubject, String> career = new TableColumn<>("Carrera");
        TableColumn<TeacherSubject, Integer> semester = new TableColumn<>("Sem.");
        TableColumn<TeacherSubject, Integer> credits = new TableColumn<>("Créditos");
        TableColumn<TeacherSubject, String> groups = new TableColumn<>("La imparte en");
        ListSupport.text(name, TeacherSubject::subjectName);
        ListSupport.text(career, TeacherSubject::careerName);
        ListSupport.value(semester, TeacherSubject::semester);
        ListSupport.value(credits, TeacherSubject::credits);
        ListSupport.text(groups, s -> s.isAssignedToGroups() ? s.groups() : "—");
        name.setPrefWidth(220);
        career.setPrefWidth(180);
        semester.setPrefWidth(50);
        credits.setPrefWidth(70);
        groups.setPrefWidth(180);
        table.getColumns().setAll(List.of(name, career, semester, credits, groups));
        table.setPlaceholder(new Label("Este profesor aún no tiene materias asignadas."));
        VBox.setVgrow(table, Priority.ALWAYS);

        removeButton.setOnAction(e -> onRemove());
        table.getSelectionModel().selectedItemProperty().addListener((o, a, sel) -> updateRemoveState(sel));
        updateRemoveState(null);

        available.setPromptText("Selecciona una materia para agregar");
        available.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(available, Priority.ALWAYS);
        addButton.getStyleClass().add("accent");
        addButton.disableProperty().bind(available.valueProperty().isNull());
        addButton.setOnAction(e -> onAdd());

        status.getStyleClass().add("form-note");
        status.setWrapText(true);

        HBox addRow = new HBox(10, available, addButton);
        addRow.setAlignment(Pos.CENTER_LEFT);
        HBox removeRow = new HBox(10, status, removeButton);
        removeRow.setAlignment(Pos.CENTER_RIGHT);
        HBox.setHgrow(status, Priority.ALWAYS);
        status.setMaxWidth(Double.MAX_VALUE);

        VBox box = new VBox(12, addRow, table, removeRow);
        box.setPadding(new Insets(8, 4, 4, 4));
        return box;
    }

    private void updateRemoveState(TeacherSubject sel) {
        removeButton.setDisable(sel == null || sel.isAssignedToGroups());
        if (sel != null && sel.isAssignedToGroups()) {
            status.setText("No se puede quitar «" + sel.subjectName() + "»: la imparte en " + sel.groups()
                    + ". Primero asigna otro profesor a esos grupos.");
        } else {
            status.setText("");
        }
    }

    /** Recarga las materias del profesor y, la primera vez, el catálogo completo. */
    private record Loaded(List<TeacherSubject> assigned, List<Catalog.Subject> catalog) {
    }

    private void reload(boolean withCatalog) {
        setBusy(true);
        Async.run(() -> new Loaded(
                        ctx.teachers().subjects(teacher.idTeacher()),
                        withCatalog ? ctx.catalog().subjects() : null),
                result -> {
                    if (result.catalog() != null) {
                        catalog = result.catalog();
                    }
                    table.getItems().setAll(result.assigned());
                    Set<Integer> ids = result.assigned().stream().map(TeacherSubject::idSubject)
                            .collect(Collectors.toSet());
                    available.getItems().setAll(catalog.stream().filter(s -> !ids.contains(s.idSubject())).toList());
                    available.setValue(null);
                    setBusy(false);
                },
                error -> {
                    setBusy(false);
                    Dialogs.error(table, error);
                });
    }

    private void onAdd() {
        Catalog.Subject subject = available.getValue();
        if (subject == null) {
            return;
        }
        run(() -> {
            ctx.teachers().addSubject(teacher.idTeacher(), subject.idSubject(), session);
            return null;
        });
    }

    private void onRemove() {
        TeacherSubject sel = table.getSelectionModel().getSelectedItem();
        if (sel == null || !Dialogs.confirm(table, "Quitar materia",
                "¿Quitar «" + sel.subjectName() + "» de las materias que puede impartir " + teacher.fullName() + "?",
                "Quitar")) {
            return;
        }
        run(() -> {
            ctx.teachers().removeSubject(teacher.idTeacher(), sel.idSubject(), session);
            return null;
        });
    }

    private void run(Callable<?> work) {
        setBusy(true);
        Async.run(work,
                r -> reload(false),
                error -> {
                    Dialogs.error(table, error);
                    reload(false);
                });
    }

    private void setBusy(boolean busy) {
        table.setDisable(busy);
        available.setDisable(busy);
        if (busy) {
            removeButton.setDisable(true);
        } else {
            updateRemoveState(table.getSelectionModel().getSelectedItem());
        }
    }
}
