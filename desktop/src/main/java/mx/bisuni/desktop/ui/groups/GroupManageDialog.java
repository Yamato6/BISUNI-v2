package mx.bisuni.desktop.ui.groups;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import mx.bisuni.desktop.AppContext;
import mx.bisuni.desktop.model.Catalog;
import mx.bisuni.desktop.model.GroupRow;
import mx.bisuni.desktop.model.GroupSubjectRow;
import mx.bisuni.desktop.model.StudentRow;
import mx.bisuni.desktop.model.SubjectRow;
import mx.bisuni.desktop.session.UserSession;
import mx.bisuni.desktop.ui.Async;
import mx.bisuni.desktop.ui.Dialogs;
import mx.bisuni.desktop.ui.ListSupport;
import mx.bisuni.desktop.ui.Navigator;
import mx.bisuni.desktop.ui.Option;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * Administración de un grupo: sus materias (con quién las imparte) y sus alumnos. Cada cambio se
 * guarda al momento.
 */
final class GroupManageDialog {

    private final AppContext ctx;
    private final UserSession session;
    private final GroupRow group;
    private final Dialog<ButtonType> dialog = new Dialog<>();

    // Materias
    private final TableView<GroupSubjectRow> subjectTable = new TableView<>();
    private final ComboBox<SubjectRow> availableSubjects = new ComboBox<>();
    private final Button addSubject = new Button("Agregar materia");
    private final Button changeTeacher = new Button("Asignar profesor…");
    private final Button removeSubject = new Button("Quitar materia");
    private final Label subjectNote = new Label();

    // Alumnos
    private final TableView<StudentRow> studentTable = new TableView<>();
    private final ComboBox<StudentRow> candidates = new ComboBox<>();
    private final Button addStudent = new Button("Agregar al grupo");
    private final Button removeStudent = new Button("Quitar del grupo");
    private final Label studentsTabTitle = new Label();

    private GroupManageDialog(Node owner, AppContext ctx, UserSession session, GroupRow group) {
        this.ctx = ctx;
        this.session = session;
        this.group = group;
        dialog.setTitle("Grupo " + group.name());
        dialog.setHeaderText(null);
        dialog.setResizable(true);
        if (owner != null && owner.getScene() != null) {
            dialog.initOwner(owner.getScene().getWindow());
        }
        dialog.getDialogPane().getButtonTypes().add(new ButtonType("Cerrar", ButtonBar.ButtonData.CANCEL_CLOSE));
        dialog.getDialogPane().getStylesheets().add(Navigator.resource("css/bisuni.css").toExternalForm());

        Label title = new Label(group.name() + " — " + group.careerName() + ", " + group.semester() + "° semestre");
        title.getStyleClass().add("view-title");
        Label tutor = new Label("Tutor: " + (group.tutorName() == null ? "sin asignar" : group.tutorName()));
        tutor.getStyleClass().add("view-subtitle");

        TabPane tabs = new TabPane(
                new Tab("Materias y profesores", buildSubjectsTab()),
                new Tab("Alumnos", buildStudentsTab()));
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        VBox.setVgrow(tabs, Priority.ALWAYS);

        VBox content = new VBox(8, title, tutor, tabs);
        content.setPadding(new Insets(4));
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().setPrefSize(900, 600);
    }

    static void show(Node owner, AppContext ctx, UserSession session, GroupRow group) {
        GroupManageDialog d = new GroupManageDialog(owner, ctx, session, group);
        d.reloadSubjects();
        d.reloadStudents();
        d.dialog.showAndWait();
    }

    // =====================================================================
    // Materias y profesores
    // =====================================================================

    private VBox buildSubjectsTab() {
        TableColumn<GroupSubjectRow, String> name = new TableColumn<>("Materia");
        TableColumn<GroupSubjectRow, Integer> sem = new TableColumn<>("Sem.");
        TableColumn<GroupSubjectRow, String> teacher = new TableColumn<>("Profesor");
        TableColumn<GroupSubjectRow, String> hours = new TableColumn<>("En horario");
        TableColumn<GroupSubjectRow, Integer> grades = new TableColumn<>("Calificaciones");
        ListSupport.text(name, GroupSubjectRow::subjectName);
        ListSupport.value(sem, GroupSubjectRow::semester);
        ListSupport.text(teacher, s -> s.hasTeacher() ? s.teacherName() : "— Sin asignar —");
        ListSupport.text(hours, s -> s.scheduledHours() + " de " + s.weeklyHours() + " h");
        ListSupport.value(grades, GroupSubjectRow::gradeCount);
        name.setPrefWidth(250);
        sem.setPrefWidth(50);
        teacher.setPrefWidth(200);
        hours.setPrefWidth(100);
        grades.setPrefWidth(110);
        subjectTable.getColumns().setAll(List.of(name, sem, teacher, hours, grades));
        subjectTable.setPlaceholder(new Label("El grupo todavía no tiene materias."));
        VBox.setVgrow(subjectTable, Priority.ALWAYS);
        subjectTable.getSelectionModel().selectedItemProperty().addListener((o, a, s) -> updateSubjectButtons());
        ListSupport.onDoubleClick(subjectTable, s -> onChangeTeacher());

        availableSubjects.setPromptText("Materias de " + group.careerName() + " que el grupo aún no cursa");
        availableSubjects.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(availableSubjects, Priority.ALWAYS);
        addSubject.getStyleClass().add("accent");
        addSubject.disableProperty().bind(availableSubjects.valueProperty().isNull());
        addSubject.setOnAction(e -> onAddSubject());
        changeTeacher.setOnAction(e -> onChangeTeacher());
        removeSubject.setOnAction(e -> onRemoveSubject());

        subjectNote.getStyleClass().add("form-note");
        subjectNote.setWrapText(true);
        subjectNote.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(subjectNote, Priority.ALWAYS);
        updateSubjectButtons();

        HBox addRow = new HBox(10, availableSubjects, addSubject);
        addRow.setAlignment(Pos.CENTER_LEFT);
        HBox actions = new HBox(10, subjectNote, changeTeacher, removeSubject);
        actions.setAlignment(Pos.CENTER_RIGHT);
        VBox box = new VBox(10, addRow, subjectTable, actions);
        box.setPadding(new Insets(10, 0, 0, 0));
        return box;
    }

    private void updateSubjectButtons() {
        GroupSubjectRow s = subjectTable.getSelectionModel().getSelectedItem();
        changeTeacher.setDisable(s == null);
        removeSubject.setDisable(s == null || s.gradeCount() > 0);
        if (s == null) {
            subjectNote.setText("Una materia necesita profesor para poder ponerse en el horario.");
        } else if (s.gradeCount() > 0) {
            subjectNote.setText("«" + s.subjectName() + "» ya tiene calificaciones en este grupo; no se puede quitar.");
        } else {
            subjectNote.setText("");
        }
    }

    private void reloadSubjects() {
        record Loaded(List<GroupSubjectRow> assigned, List<SubjectRow> available) {
        }
        setSubjectsBusy(true);
        Async.run(() -> new Loaded(ctx.groups().subjects(group.idGroup()),
                        ctx.groups().availableSubjects(group.idGroup())),
                r -> {
                    Integer selected = subjectTable.getSelectionModel().getSelectedItem() == null
                            ? null : subjectTable.getSelectionModel().getSelectedItem().idSubject();
                    subjectTable.getItems().setAll(r.assigned());
                    if (selected != null) {
                        r.assigned().stream().filter(s -> s.idSubject() == selected).findFirst()
                                .ifPresent(s -> subjectTable.getSelectionModel().select(s));
                    }
                    availableSubjects.getItems().setAll(r.available());
                    availableSubjects.setValue(null);
                    setSubjectsBusy(false);
                },
                error -> {
                    setSubjectsBusy(false);
                    Dialogs.error(subjectTable, error);
                });
    }

    private void onAddSubject() {
        SubjectRow s = availableSubjects.getValue();
        if (s != null) {
            runSubjects(() -> {
                ctx.groups().addSubject(group.idGroup(), s.idSubject(), session);
                return null;
            });
        }
    }

    private void onRemoveSubject() {
        GroupSubjectRow s = subjectTable.getSelectionModel().getSelectedItem();
        if (s == null) {
            return;
        }
        String msg = "¿Quitar «" + s.subjectName() + "» del grupo " + group.name() + "?";
        if (s.scheduledHours() > 0) {
            msg += "\n\nTambién se borrarán sus " + s.scheduledHours() + " clases del horario del grupo.";
        }
        if (Dialogs.confirm(subjectTable, "Quitar materia", msg, "Quitar")) {
            runSubjects(() -> {
                ctx.groups().removeSubject(group.idGroup(), s.idSubject(), session);
                return null;
            });
        }
    }

    /** Elige el profesor entre los habilitados para la materia. */
    private void onChangeTeacher() {
        GroupSubjectRow s = subjectTable.getSelectionModel().getSelectedItem();
        if (s == null) {
            return;
        }
        Async.run(() -> ctx.groups().eligibleTeachers(s.idSubject()),
                teachers -> chooseTeacher(s, teachers),
                error -> Dialogs.error(subjectTable, error));
    }

    private void chooseTeacher(GroupSubjectRow s, List<Catalog.Teacher> teachers) {
        if (teachers.isEmpty() && !s.hasTeacher()) {
            Dialogs.info(subjectTable, "Sin profesores habilitados", "Ningún profesor activo está habilitado para "
                    + "impartir «" + s.subjectName() + "».\n\nHabilítalo en Profesores → Materias… y vuelve aquí.");
            return;
        }
        List<Option<Catalog.Teacher>> options = new ArrayList<>();
        Option<Catalog.Teacher> none = new Option<>("— Sin asignar —", null);
        options.add(none);
        Option<Catalog.Teacher> current = none;
        for (Catalog.Teacher t : teachers) {
            Option<Catalog.Teacher> o = new Option<>(t.name(), t);
            options.add(o);
            if (s.hasTeacher() && t.idTeacher() == s.idTeacher()) {
                current = o;
            }
        }
        ChoiceDialog<Option<Catalog.Teacher>> choice = new ChoiceDialog<>(current, options);
        choice.initOwner(dialog.getDialogPane().getScene().getWindow());
        choice.setTitle("Profesor de " + s.subjectName());
        choice.setHeaderText("¿Quién imparte «" + s.subjectName() + "» en " + group.name() + "?");
        choice.setContentText("Profesor:");
        String note = "Solo aparecen los profesores habilitados para esta materia.";
        if (s.scheduledHours() > 0) {
            note += " El horario del grupo se actualiza solo; si el profesor ya tiene clase a esa hora, no se podrá asignar.";
        }
        Label noteLabel = new Label(note);
        noteLabel.setWrapText(true);
        noteLabel.setMaxWidth(420);
        noteLabel.getStyleClass().add("form-note");
        choice.getDialogPane().setExpandableContent(null);
        choice.getDialogPane().setContent(new VBox(10, choice.getDialogPane().getContent(), noteLabel));
        choice.getDialogPane().getStylesheets().add(Navigator.resource("css/bisuni.css").toExternalForm());

        choice.showAndWait().ifPresent(selected -> {
            Integer idTeacher = selected.value() == null ? null : selected.value().idTeacher();
            if (java.util.Objects.equals(idTeacher, s.idTeacher())) {
                return;
            }
            runSubjects(() -> {
                ctx.groups().assignTeacher(group.idGroup(), s.idSubject(), idTeacher, session);
                return null;
            });
        });
    }

    private void runSubjects(Callable<?> work) {
        setSubjectsBusy(true);
        Async.run(work,
                r -> reloadSubjects(),
                error -> {
                    Dialogs.error(subjectTable, error);
                    reloadSubjects();
                });
    }

    private void setSubjectsBusy(boolean busy) {
        subjectTable.setDisable(busy);
        availableSubjects.setDisable(busy);
        if (busy) {
            changeTeacher.setDisable(true);
            removeSubject.setDisable(true);
        } else {
            updateSubjectButtons();
        }
    }

    // =====================================================================
    // Alumnos
    // =====================================================================

    private VBox buildStudentsTab() {
        TableColumn<StudentRow, String> name = new TableColumn<>("Nombre");
        TableColumn<StudentRow, String> username = new TableColumn<>("Usuario");
        TableColumn<StudentRow, String> email = new TableColumn<>("Correo");
        TableColumn<StudentRow, BigDecimal> gpa = new TableColumn<>("Promedio");
        ListSupport.text(name, s -> s.lastName() + ", " + s.firstName());
        ListSupport.text(username, StudentRow::username);
        ListSupport.text(email, StudentRow::email);
        ListSupport.formatted(gpa, StudentRow::gpa, BigDecimal::toPlainString);
        name.setPrefWidth(250);
        username.setPrefWidth(130);
        email.setPrefWidth(250);
        gpa.setPrefWidth(90);
        studentTable.getColumns().setAll(List.of(name, username, email, gpa));
        studentTable.setPlaceholder(new Label("El grupo todavía no tiene alumnos."));
        VBox.setVgrow(studentTable, Priority.ALWAYS);

        candidates.setPromptText("Alumnos activos de " + group.careerName());
        candidates.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(candidates, Priority.ALWAYS);
        candidates.setConverter(new javafx.util.StringConverter<>() {
            @Override
            public String toString(StudentRow s) {
                if (s == null) {
                    return "";
                }
                return s.lastName() + ", " + s.firstName() + " (" + s.username() + ")"
                        + (s.groupName() == null ? " — sin grupo" : " — actualmente en " + s.groupName());
            }

            @Override
            public StudentRow fromString(String string) {
                return null;
            }
        });
        addStudent.getStyleClass().add("accent");
        addStudent.disableProperty().bind(candidates.valueProperty().isNull());
        addStudent.setOnAction(e -> onAddStudent());
        removeStudent.disableProperty().bind(studentTable.getSelectionModel().selectedItemProperty().isNull());
        removeStudent.setOnAction(e -> onRemoveStudent());

        studentsTabTitle.getStyleClass().add("form-note");
        HBox addRow = new HBox(10, candidates, addStudent);
        addRow.setAlignment(Pos.CENTER_LEFT);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox actions = new HBox(10, studentsTabTitle, spacer, removeStudent);
        actions.setAlignment(Pos.CENTER_RIGHT);
        VBox box = new VBox(10, addRow, studentTable, actions);
        box.setPadding(new Insets(10, 0, 0, 0));
        return box;
    }

    private void reloadStudents() {
        record Loaded(List<StudentRow> members, List<StudentRow> candidates) {
        }
        setStudentsBusy(true);
        Async.run(() -> new Loaded(ctx.groups().students(group.idGroup()),
                        ctx.groups().candidateStudents(group.idGroup())),
                r -> {
                    studentTable.getItems().setAll(r.members());
                    // Primero los que no tienen grupo, que son los candidatos naturales.
                    List<StudentRow> sorted = new ArrayList<>(r.candidates());
                    sorted.sort(java.util.Comparator.comparing((StudentRow s) -> s.groupName() != null)
                            .thenComparing(StudentRow::lastName).thenComparing(StudentRow::firstName));
                    candidates.getItems().setAll(sorted);
                    candidates.setValue(null);
                    studentsTabTitle.setText(ListSupport.count(r.members().size(), "alumno", "alumnos")
                            + ". Quitar a un alumno del grupo no borra sus calificaciones.");
                    setStudentsBusy(false);
                },
                error -> {
                    setStudentsBusy(false);
                    Dialogs.error(studentTable, error);
                });
    }

    private void onAddStudent() {
        StudentRow s = candidates.getValue();
        if (s == null) {
            return;
        }
        if (s.groupName() != null && !Dialogs.confirm(studentTable, "Cambiar de grupo",
                s.firstName() + " " + s.lastName() + " está en " + s.groupName() + ". ¿Moverlo a " + group.name() + "?",
                "Mover")) {
            return;
        }
        runStudents(() -> {
            ctx.groups().addStudent(group.idGroup(), s.idStudent(), session);
            return null;
        });
    }

    private void onRemoveStudent() {
        StudentRow s = studentTable.getSelectionModel().getSelectedItem();
        if (s != null && Dialogs.confirm(studentTable, "Quitar del grupo",
                "¿Quitar a " + s.firstName() + " " + s.lastName() + " del grupo " + group.name()
                        + "? Quedará sin grupo; sus calificaciones se conservan.", "Quitar")) {
            runStudents(() -> {
                ctx.groups().removeStudent(group.idGroup(), s.idStudent(), session);
                return null;
            });
        }
    }

    private void runStudents(Callable<?> work) {
        setStudentsBusy(true);
        Async.run(work,
                r -> reloadStudents(),
                error -> {
                    Dialogs.error(studentTable, error);
                    reloadStudents();
                });
    }

    private void setStudentsBusy(boolean busy) {
        studentTable.setDisable(busy);
        candidates.setDisable(busy);
    }
}
