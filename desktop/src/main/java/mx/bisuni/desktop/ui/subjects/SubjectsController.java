package mx.bisuni.desktop.ui.subjects;

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
import mx.bisuni.desktop.dao.SubjectDao;
import mx.bisuni.desktop.model.Catalog;
import mx.bisuni.desktop.model.RecordStatus;
import mx.bisuni.desktop.model.SubjectRow;
import mx.bisuni.desktop.session.UserSession;
import mx.bisuni.desktop.ui.Async;
import mx.bisuni.desktop.ui.Dialogs;
import mx.bisuni.desktop.ui.ListSupport;
import mx.bisuni.desktop.ui.Option;
import mx.bisuni.desktop.ui.shell.ShellController;

import java.util.List;
import java.util.stream.IntStream;

public class SubjectsController implements ShellController.ContentView {

    @FXML private VBox root;
    @FXML private TextField searchField;
    @FXML private ComboBox<Option<Catalog.Career>> careerCombo;
    @FXML private ComboBox<Option<Integer>> semesterCombo;
    @FXML private ComboBox<Option<RecordStatus>> statusCombo;
    @FXML private Label countLabel;
    @FXML private TableView<SubjectRow> table;
    @FXML private Label placeholderLabel;
    @FXML private TableColumn<SubjectRow, String> nameCol;
    @FXML private TableColumn<SubjectRow, String> careerCol;
    @FXML private TableColumn<SubjectRow, Integer> semesterCol;
    @FXML private TableColumn<SubjectRow, Integer> creditsCol;
    @FXML private TableColumn<SubjectRow, Integer> hoursCol;
    @FXML private TableColumn<SubjectRow, Integer> groupsCol;
    @FXML private TableColumn<SubjectRow, Integer> teachersCol;
    @FXML private TableColumn<SubjectRow, RecordStatus> statusCol;
    @FXML private Button editButton;

    private AppContext ctx;
    private UserSession session;
    private List<Catalog.Career> careers = List.of();
    private int loadSeq;

    @FXML
    private void initialize() {
        ListSupport.text(nameCol, SubjectRow::name);
        ListSupport.text(careerCol, SubjectRow::careerName);
        ListSupport.value(semesterCol, SubjectRow::semester);
        ListSupport.value(creditsCol, SubjectRow::credits);
        ListSupport.value(hoursCol, SubjectRow::weeklyHours);
        ListSupport.value(groupsCol, SubjectRow::groupCount);
        ListSupport.value(teachersCol, SubjectRow::teacherCount);
        ListSupport.value(statusCol, SubjectRow::status);
        ListSupport.onDoubleClick(table, this::edit);

        semesterCombo.getItems().setAll(Option.withAll("Todos", IntStream.rangeClosed(1, 12).boxed().toList()));
        semesterCombo.getSelectionModel().selectFirst();
        statusCombo.getItems().setAll(Option.withAll("Todos", List.of(RecordStatus.values())));
        statusCombo.getSelectionModel().selectFirst();
        editButton.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
    }

    public void init(AppContext ctx, UserSession session) {
        this.ctx = ctx;
        this.session = session;
        Async.run(() -> ctx.catalog().careers(),
                c -> {
                    careers = c;
                    careerCombo.getItems().setAll(Option.withAll("Todas", c));
                    careerCombo.getSelectionModel().selectFirst();
                    careerCombo.valueProperty().addListener((o, a, b) -> load());
                    semesterCombo.valueProperty().addListener((o, a, b) -> load());
                    statusCombo.valueProperty().addListener((o, a, b) -> load());
                    ListSupport.debounce(searchField, this::load);
                    load();
                },
                error -> {
                    placeholderLabel.setText("No se pudo cargar el catálogo de carreras.");
                    Dialogs.error(root, error);
                });
    }

    @Override
    public Node root() {
        return root;
    }

    @FXML
    private void onNew() {
        if (SubjectForm.show(root, ctx, session, careers, null)) {
            load();
        }
    }

    @FXML
    private void onEdit() {
        SubjectRow selected = table.getSelectionModel().getSelectedItem();
        if (selected != null) {
            edit(selected);
        }
    }

    private void edit(SubjectRow row) {
        if (SubjectForm.show(root, ctx, session, careers, row)) {
            load();
        }
    }

    private void load() {
        Catalog.Career career = careerCombo.getValue() == null ? null : careerCombo.getValue().value();
        var filter = new SubjectDao.Filter(
                career == null ? null : career.idCareer(),
                semesterCombo.getValue() == null ? null : semesterCombo.getValue().value(),
                statusCombo.getValue() == null ? null : statusCombo.getValue().value(),
                searchField.getText());
        Integer selectedId = table.getSelectionModel().getSelectedItem() == null
                ? null : table.getSelectionModel().getSelectedItem().idSubject();
        int seq = ++loadSeq;
        placeholderLabel.setText("Cargando…");
        Async.run(() -> ctx.subjects().list(filter),
                rows -> {
                    if (seq != loadSeq) {
                        return;
                    }
                    table.getItems().setAll(rows);
                    placeholderLabel.setText("No hay materias con estos filtros.");
                    countLabel.setText(ListSupport.count(rows.size(), "materia", "materias"));
                    if (selectedId != null) {
                        rows.stream().filter(r -> r.idSubject() == selectedId).findFirst()
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
