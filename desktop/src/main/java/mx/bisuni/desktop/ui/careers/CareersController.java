package mx.bisuni.desktop.ui.careers;

import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import mx.bisuni.desktop.AppContext;
import mx.bisuni.desktop.model.CareerData;
import mx.bisuni.desktop.model.CareerRow;
import mx.bisuni.desktop.model.RecordStatus;
import mx.bisuni.desktop.session.UserSession;
import mx.bisuni.desktop.ui.Async;
import mx.bisuni.desktop.ui.Dialogs;
import mx.bisuni.desktop.ui.FormDialog;
import mx.bisuni.desktop.ui.ListSupport;
import mx.bisuni.desktop.ui.Option;
import mx.bisuni.desktop.ui.shell.ShellController;

import java.util.List;

public class CareersController implements ShellController.ContentView {

    @FXML private VBox root;
    @FXML private ComboBox<Option<RecordStatus>> statusCombo;
    @FXML private Label countLabel;
    @FXML private TableView<CareerRow> table;
    @FXML private Label placeholderLabel;
    @FXML private TableColumn<CareerRow, String> nameCol;
    @FXML private TableColumn<CareerRow, String> codeCol;
    @FXML private TableColumn<CareerRow, Integer> semestersCol;
    @FXML private TableColumn<CareerRow, Integer> subjectsCol;
    @FXML private TableColumn<CareerRow, Integer> studentsCol;
    @FXML private TableColumn<CareerRow, RecordStatus> statusCol;
    @FXML private Button editButton;

    private AppContext ctx;
    private UserSession session;
    private int loadSeq;

    @FXML
    private void initialize() {
        ListSupport.text(nameCol, CareerRow::name);
        ListSupport.text(codeCol, CareerRow::code);
        ListSupport.value(semestersCol, CareerRow::totalSemesters);
        ListSupport.value(subjectsCol, CareerRow::subjectCount);
        ListSupport.value(studentsCol, CareerRow::studentCount);
        ListSupport.value(statusCol, CareerRow::status);
        ListSupport.onDoubleClick(table, this::edit);
        statusCombo.getItems().setAll(Option.withAll("Todas", List.of(RecordStatus.values())));
        statusCombo.getSelectionModel().selectFirst();
        editButton.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
    }

    public void init(AppContext ctx, UserSession session) {
        this.ctx = ctx;
        this.session = session;
        statusCombo.valueProperty().addListener((o, a, b) -> load());
        load();
    }

    @Override
    public Node root() {
        return root;
    }

    @FXML
    private void onNew() {
        if (showForm(null)) {
            load();
        }
    }

    @FXML
    private void onEdit() {
        CareerRow selected = table.getSelectionModel().getSelectedItem();
        if (selected != null) {
            edit(selected);
        }
    }

    private void edit(CareerRow row) {
        if (showForm(row)) {
            load();
        }
    }

    private boolean showForm(CareerRow career) {
        boolean creating = career == null;
        FormDialog form = new FormDialog(root, creating ? "Nueva carrera" : "Editar carrera",
                creating ? "Crear carrera" : "Guardar");
        TextField name = form.add("Nombre", new TextField(creating ? "" : career.name()));
        TextField code = form.add("Clave", new TextField(creating ? "" : career.code()));
        code.setPromptText("2 a 4 letras, p. ej. IT");
        Spinner<Integer> semesters = form.add("Semestres", new Spinner<>(1, 12, creating ? 9 : career.totalSemesters()));
        ComboBox<RecordStatus> status = form.add("Estatus", new ComboBox<>());
        status.getItems().setAll(RecordStatus.values());
        status.setValue(creating ? RecordStatus.Active : career.status());
        if (!creating && career.studentCount() > 0) {
            code.setDisable(true);
            form.addFull(FormDialog.note("La clave no se puede cambiar: forma parte de la matrícula de sus "
                    + career.studentCount() + " alumnos."));
        }
        form.addFull(FormDialog.note("Al promover un grupo del último semestre, sus alumnos egresan."));
        return form.showAndSave(() -> {
            var data = new CareerData(name.getText(), code.getText(), semesters.getValue(), status.getValue());
            return () -> {
                if (creating) {
                    ctx.careers().create(data, session);
                } else {
                    ctx.careers().update(career.idCareer(), data, session);
                }
                return null;
            };
        });
    }

    private void load() {
        RecordStatus status = statusCombo.getValue() == null ? null : statusCombo.getValue().value();
        int seq = ++loadSeq;
        placeholderLabel.setText("Cargando…");
        Async.run(() -> ctx.careers().list(status),
                rows -> {
                    if (seq != loadSeq) {
                        return;
                    }
                    table.getItems().setAll(rows);
                    placeholderLabel.setText("No hay carreras con este filtro.");
                    countLabel.setText(ListSupport.count(rows.size(), "carrera", "carreras"));
                },
                error -> {
                    if (seq != loadSeq) {
                        return;
                    }
                    placeholderLabel.setText("No se pudo cargar la lista.");
                    Dialogs.error(root, error);
                });
    }
}
