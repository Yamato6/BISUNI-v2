package mx.bisuni.desktop.ui.teachers;

import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import mx.bisuni.desktop.AppContext;
import mx.bisuni.desktop.model.RecordStatus;
import mx.bisuni.desktop.model.TeacherRow;
import mx.bisuni.desktop.session.UserSession;
import mx.bisuni.desktop.ui.Async;
import mx.bisuni.desktop.ui.Dialogs;
import mx.bisuni.desktop.ui.ListSupport;
import mx.bisuni.desktop.ui.Option;
import mx.bisuni.desktop.ui.shell.ShellController;

import java.util.List;

public class TeachersController implements ShellController.ContentView {

    @FXML private VBox root;
    @FXML private TextField searchField;
    @FXML private ComboBox<Option<RecordStatus>> statusCombo;
    @FXML private Label countLabel;
    @FXML private TableView<TeacherRow> table;
    @FXML private Label placeholderLabel;
    @FXML private TableColumn<TeacherRow, String> nameCol;
    @FXML private TableColumn<TeacherRow, String> usernameCol;
    @FXML private TableColumn<TeacherRow, String> emailCol;
    @FXML private TableColumn<TeacherRow, String> specialtyCol;
    @FXML private TableColumn<TeacherRow, String> phoneCol;
    @FXML private TableColumn<TeacherRow, Integer> subjectsCol;
    @FXML private TableColumn<TeacherRow, RecordStatus> statusCol;
    @FXML private Button editButton;
    @FXML private Button subjectsButton;

    private AppContext ctx;
    private UserSession session;
    private int loadSeq;

    @FXML
    private void initialize() {
        ListSupport.text(nameCol, t -> t.lastName() + ", " + t.firstName());
        ListSupport.text(usernameCol, TeacherRow::username);
        ListSupport.text(emailCol, TeacherRow::email);
        ListSupport.text(specialtyCol, t -> t.specialty() == null ? "" : t.specialty());
        ListSupport.text(phoneCol, t -> t.phone() == null ? "" : t.phone());
        ListSupport.value(subjectsCol, TeacherRow::subjectCount);
        ListSupport.value(statusCol, TeacherRow::status);
        ListSupport.onDoubleClick(table, this::edit);

        statusCombo.getItems().setAll(Option.withAll("Todos", List.of(RecordStatus.values())));
        statusCombo.getSelectionModel().selectFirst();
        var noSelection = table.getSelectionModel().selectedItemProperty().isNull();
        editButton.disableProperty().bind(noSelection);
        subjectsButton.disableProperty().bind(noSelection);
    }

    public void init(AppContext ctx, UserSession session) {
        this.ctx = ctx;
        this.session = session;
        statusCombo.valueProperty().addListener((o, a, b) -> load());
        ListSupport.debounce(searchField, this::load);
        load();
    }

    @Override
    public Node root() {
        return root;
    }

    @FXML
    private void onEdit() {
        TeacherRow selected = table.getSelectionModel().getSelectedItem();
        if (selected != null) {
            edit(selected);
        }
    }

    private void edit(TeacherRow row) {
        if (TeacherForms.edit(root, ctx, session, row)) {
            load();
        }
    }

    @FXML
    private void onSubjects() {
        TeacherRow selected = table.getSelectionModel().getSelectedItem();
        if (selected != null) {
            // Los cambios de materias se aplican al momento; al cerrar se refresca el conteo.
            TeacherSubjectsDialog.show(root, ctx, session, selected, this::load);
        }
    }

    private void load() {
        RecordStatus status = statusCombo.getValue() == null ? null : statusCombo.getValue().value();
        String search = searchField.getText();
        Integer selectedId = table.getSelectionModel().getSelectedItem() == null
                ? null : table.getSelectionModel().getSelectedItem().idTeacher();
        int seq = ++loadSeq;
        placeholderLabel.setText("Cargando…");
        Async.run(() -> ctx.teachers().list(status, search),
                rows -> {
                    if (seq != loadSeq) {
                        return;
                    }
                    table.getItems().setAll(rows);
                    placeholderLabel.setText("No hay profesores con estos filtros.");
                    countLabel.setText(ListSupport.count(rows.size(), "profesor", "profesores"));
                    if (selectedId != null) {
                        rows.stream().filter(r -> r.idTeacher() == selectedId).findFirst()
                                .ifPresent(r -> table.getSelectionModel().select(r));
                    }
                },
                error -> {
                    if (seq != loadSeq) {
                        return;
                    }
                    table.getItems().clear();
                    placeholderLabel.setText("No se pudo cargar la lista.");
                    Dialogs.error(root, error);
                });
    }
}
