package mx.bisuni.desktop.ui.teacher;

import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.VBox;
import mx.bisuni.desktop.AppContext;
import mx.bisuni.desktop.model.TeacherClass;
import mx.bisuni.desktop.session.UserSession;
import mx.bisuni.desktop.ui.Async;
import mx.bisuni.desktop.ui.Dialogs;
import mx.bisuni.desktop.ui.ListSupport;
import mx.bisuni.desktop.ui.shell.ShellController;

import java.util.function.Consumer;

/** Inicio del profesor: sus clases (materia + grupo) con avance de horario y calificaciones. */
public class MyClassesController implements ShellController.ContentView {

    @FXML private VBox root;
    @FXML private Label titleLabel;
    @FXML private TableView<TeacherClass> table;
    @FXML private Label placeholderLabel;
    @FXML private TableColumn<TeacherClass, String> groupCol;
    @FXML private TableColumn<TeacherClass, String> subjectCol;
    @FXML private TableColumn<TeacherClass, String> careerCol;
    @FXML private TableColumn<TeacherClass, Integer> semesterCol;
    @FXML private TableColumn<TeacherClass, Integer> studentsCol;
    @FXML private TableColumn<TeacherClass, String> hoursCol;
    @FXML private TableColumn<TeacherClass, String> gradedCol;
    @FXML private Label countLabel;
    @FXML private Button gradesButton;

    private Consumer<TeacherClass> openGrades;

    @FXML
    private void initialize() {
        ListSupport.text(groupCol, TeacherClass::groupName);
        ListSupport.text(subjectCol, TeacherClass::subjectName);
        ListSupport.text(careerCol, TeacherClass::careerName);
        ListSupport.value(semesterCol, TeacherClass::semester);
        ListSupport.value(studentsCol, TeacherClass::studentCount);
        ListSupport.text(hoursCol, c -> c.scheduledHours() + " de " + c.weeklyHours() + " h");
        ListSupport.text(gradedCol, c -> c.gradedCount() + " de " + c.studentCount());
        ListSupport.onDoubleClick(table, c -> openGrades.accept(c));
        gradesButton.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
    }

    /** @param openGrades abre la captura de calificaciones de la clase elegida */
    public void init(AppContext ctx, UserSession session, Consumer<TeacherClass> openGrades) {
        this.openGrades = openGrades;
        titleLabel.setText("Hola, " + session.firstName());
        Async.run(() -> ctx.teaching().myClasses(session),
                classes -> {
                    table.getItems().setAll(classes);
                    placeholderLabel.setText("Todavía no tienes clases asignadas. Control escolar las asigna en Grupos.");
                    countLabel.setText(ListSupport.count(classes.size(), "clase", "clases"));
                    if (!classes.isEmpty()) {
                        table.getSelectionModel().selectFirst();
                    }
                },
                error -> {
                    placeholderLabel.setText("No se pudieron cargar tus clases.");
                    Dialogs.error(root, error);
                });
    }

    @Override
    public Node root() {
        return root;
    }

    @FXML
    private void onGrades() {
        TeacherClass selected = table.getSelectionModel().getSelectedItem();
        if (selected != null) {
            openGrades.accept(selected);
        }
    }
}
