package mx.bisuni.desktop.ui.teacher;

import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.VBox;
import mx.bisuni.desktop.AppContext;
import mx.bisuni.desktop.model.RecordStatus;
import mx.bisuni.desktop.model.StudentRow;
import mx.bisuni.desktop.model.TeacherGroup;
import mx.bisuni.desktop.session.UserSession;
import mx.bisuni.desktop.ui.Async;
import mx.bisuni.desktop.ui.Dialogs;
import mx.bisuni.desktop.ui.ListSupport;
import mx.bisuni.desktop.ui.shell.ShellController;

import java.math.BigDecimal;

/** Grupos del profesor (donde da clase o es tutor) y sus alumnos. */
public class MyGroupsController implements ShellController.ContentView {

    @FXML private VBox root;
    @FXML private TableView<TeacherGroup> table;
    @FXML private Label placeholderLabel;
    @FXML private TableColumn<TeacherGroup, String> nameCol;
    @FXML private TableColumn<TeacherGroup, String> careerCol;
    @FXML private TableColumn<TeacherGroup, Integer> semesterCol;
    @FXML private TableColumn<TeacherGroup, String> tutorCol;
    @FXML private TableColumn<TeacherGroup, Integer> studentsCol;
    @FXML private TableColumn<TeacherGroup, String> subjectsCol;
    @FXML private Label studentsTitle;
    @FXML private TableView<StudentRow> studentTable;
    @FXML private Label studentsPlaceholder;
    @FXML private TableColumn<StudentRow, String> studentNameCol;
    @FXML private TableColumn<StudentRow, String> studentUserCol;
    @FXML private TableColumn<StudentRow, String> studentEmailCol;
    @FXML private TableColumn<StudentRow, BigDecimal> studentGpaCol;
    @FXML private TableColumn<StudentRow, RecordStatus> studentStatusCol;

    private AppContext ctx;
    private UserSession session;
    private int studentsSeq;

    @FXML
    private void initialize() {
        ListSupport.text(nameCol, TeacherGroup::name);
        ListSupport.text(careerCol, TeacherGroup::careerName);
        ListSupport.value(semesterCol, TeacherGroup::semester);
        ListSupport.text(tutorCol, g -> g.tutor() ? "Sí" : "");
        ListSupport.value(studentsCol, TeacherGroup::studentCount);
        ListSupport.text(subjectsCol, g -> g.mySubjects().isEmpty() ? "— (solo tutor)" : g.mySubjects());

        ListSupport.text(studentNameCol, s -> s.lastName() + ", " + s.firstName());
        ListSupport.text(studentUserCol, StudentRow::username);
        ListSupport.text(studentEmailCol, StudentRow::email);
        ListSupport.formatted(studentGpaCol, StudentRow::gpa, BigDecimal::toPlainString);
        ListSupport.value(studentStatusCol, StudentRow::status);

        table.getSelectionModel().selectedItemProperty().addListener((o, a, g) -> loadStudents(g));
    }

    public void init(AppContext ctx, UserSession session) {
        this.ctx = ctx;
        this.session = session;
        Async.run(() -> ctx.teaching().myGroups(session),
                groups -> {
                    table.getItems().setAll(groups);
                    placeholderLabel.setText("No impartes clases en ningún grupo ni eres tutor.");
                    if (!groups.isEmpty()) {
                        table.getSelectionModel().selectFirst();
                    }
                },
                error -> {
                    placeholderLabel.setText("No se pudieron cargar tus grupos.");
                    Dialogs.error(root, error);
                });
    }

    @Override
    public Node root() {
        return root;
    }

    private void loadStudents(TeacherGroup group) {
        int seq = ++studentsSeq;
        studentTable.getItems().clear();
        if (group == null) {
            studentsTitle.setText("");
            studentsPlaceholder.setText("Selecciona un grupo.");
            return;
        }
        studentsTitle.setText("Alumnos de " + group.name());
        studentsPlaceholder.setText("Cargando…");
        Async.run(() -> ctx.teaching().groupStudents(group.idGroup(), session),
                students -> {
                    if (seq != studentsSeq) {
                        return;
                    }
                    studentTable.getItems().setAll(students);
                    studentsTitle.setText("Alumnos de " + group.name() + " (" + students.size() + ")");
                    studentsPlaceholder.setText("El grupo no tiene alumnos.");
                },
                error -> {
                    if (seq != studentsSeq) {
                        return;
                    }
                    studentsPlaceholder.setText("No se pudieron cargar los alumnos.");
                    Dialogs.error(root, error);
                });
    }
}
