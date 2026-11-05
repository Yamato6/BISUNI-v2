package mx.bisuni.desktop.ui.students;

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
import mx.bisuni.desktop.dao.StudentDao;
import mx.bisuni.desktop.model.Catalog;
import mx.bisuni.desktop.model.RecordStatus;
import mx.bisuni.desktop.model.StudentRow;
import mx.bisuni.desktop.session.UserSession;
import mx.bisuni.desktop.ui.Async;
import mx.bisuni.desktop.ui.Dialogs;
import mx.bisuni.desktop.ui.ListSupport;
import mx.bisuni.desktop.ui.Option;
import mx.bisuni.desktop.ui.shell.ShellController;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class StudentsController implements ShellController.ContentView {

    /** Valor del filtro de grupo que significa "alumnos sin grupo". */
    private static final int WITHOUT_GROUP = -1;

    /** Catálogos que necesitan la vista y el formulario. */
    record Catalogs(List<Catalog.Career> careers, List<Catalog.Group> groups) {
        List<Catalog.Group> groupsOf(int idCareer) {
            return groups.stream().filter(g -> g.idCareer() == idCareer).toList();
        }
    }

    @FXML private VBox root;
    @FXML private TextField searchField;
    @FXML private ComboBox<Option<Catalog.Career>> careerCombo;
    @FXML private ComboBox<Option<Integer>> groupCombo;
    @FXML private ComboBox<Option<RecordStatus>> statusCombo;
    @FXML private Label countLabel;
    @FXML private TableView<StudentRow> table;
    @FXML private Label placeholderLabel;
    @FXML private TableColumn<StudentRow, String> nameCol;
    @FXML private TableColumn<StudentRow, String> usernameCol;
    @FXML private TableColumn<StudentRow, String> emailCol;
    @FXML private TableColumn<StudentRow, String> careerCol;
    @FXML private TableColumn<StudentRow, Integer> semesterCol;
    @FXML private TableColumn<StudentRow, String> groupCol;
    @FXML private TableColumn<StudentRow, BigDecimal> gpaCol;
    @FXML private TableColumn<StudentRow, RecordStatus> statusCol;
    @FXML private Button editButton;

    private AppContext ctx;
    private UserSession session;
    private Catalogs catalogs;
    private int loadSeq;
    private boolean updatingGroups;

    @FXML
    private void initialize() {
        ListSupport.text(nameCol, s -> s.lastName() + ", " + s.firstName());
        ListSupport.text(usernameCol, StudentRow::username);
        ListSupport.text(emailCol, StudentRow::email);
        ListSupport.text(careerCol, StudentRow::careerName);
        ListSupport.value(semesterCol, StudentRow::semester);
        ListSupport.text(groupCol, s -> s.groupName() == null ? "Sin grupo" : s.groupName());
        ListSupport.formatted(gpaCol, StudentRow::gpa, BigDecimal::toPlainString);
        ListSupport.value(statusCol, StudentRow::status);
        ListSupport.onDoubleClick(table, this::edit);

        statusCombo.getItems().setAll(Option.withAll("Todos", List.of(RecordStatus.values())));
        statusCombo.getSelectionModel().selectFirst();
        editButton.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
    }

    public void init(AppContext ctx, UserSession session) {
        this.ctx = ctx;
        this.session = session;
        placeholderLabel.setText("Cargando…");
        Async.run(() -> new Catalogs(ctx.catalog().careers(), ctx.catalog().groups()),
                c -> {
                    catalogs = c;
                    careerCombo.getItems().setAll(Option.withAll("Todas", c.careers()));
                    careerCombo.getSelectionModel().selectFirst();
                    refreshGroupOptions();
                    careerCombo.valueProperty().addListener((o, a, b) -> {
                        refreshGroupOptions();
                        load();
                    });
                    groupCombo.valueProperty().addListener((o, a, b) -> {
                        if (!updatingGroups) {
                            load();
                        }
                    });
                    statusCombo.valueProperty().addListener((o, a, b) -> load());
                    ListSupport.debounce(searchField, this::load);
                    load();
                },
                error -> {
                    placeholderLabel.setText("No se pudieron cargar los catálogos.");
                    Dialogs.error(root, error);
                });
    }

    @Override
    public Node root() {
        return root;
    }

    /** Las opciones de grupo dependen de la carrera elegida. */
    private void refreshGroupOptions() {
        updatingGroups = true;
        Catalog.Career career = careerCombo.getValue() == null ? null : careerCombo.getValue().value();
        List<Option<Integer>> options = new ArrayList<>();
        options.add(new Option<>("Todos", null));
        options.add(new Option<>("Sin grupo", WITHOUT_GROUP));
        List<Catalog.Group> groups = career == null ? catalogs.groups() : catalogs.groupsOf(career.idCareer());
        for (Catalog.Group g : groups) {
            options.add(new Option<>(g.name(), g.idGroup()));
        }
        groupCombo.getItems().setAll(options);
        groupCombo.getSelectionModel().selectFirst();
        updatingGroups = false;
    }

    @FXML
    private void onEdit() {
        StudentRow selected = table.getSelectionModel().getSelectedItem();
        if (selected != null) {
            edit(selected);
        }
    }

    private void edit(StudentRow row) {
        if (catalogs != null && StudentForm.edit(root, ctx, session, catalogs, row)) {
            load();
        }
    }

    private StudentDao.Filter currentFilter() {
        Catalog.Career career = careerCombo.getValue() == null ? null : careerCombo.getValue().value();
        Integer group = groupCombo.getValue() == null ? null : groupCombo.getValue().value();
        RecordStatus status = statusCombo.getValue() == null ? null : statusCombo.getValue().value();
        boolean withoutGroup = group != null && group == WITHOUT_GROUP;
        return new StudentDao.Filter(
                career == null ? null : career.idCareer(),
                withoutGroup ? null : group,
                withoutGroup,
                status,
                searchField.getText());
    }

    private void load() {
        StudentDao.Filter filter = currentFilter();
        Integer selectedId = table.getSelectionModel().getSelectedItem() == null
                ? null : table.getSelectionModel().getSelectedItem().idStudent();
        int seq = ++loadSeq;
        placeholderLabel.setText("Cargando…");
        Async.run(() -> ctx.students().list(filter),
                rows -> {
                    if (seq != loadSeq) {
                        return;
                    }
                    table.getItems().setAll(rows);
                    placeholderLabel.setText("No hay alumnos con estos filtros.");
                    countLabel.setText(ListSupport.count(rows.size(), "alumno", "alumnos"));
                    if (selectedId != null) {
                        rows.stream().filter(r -> r.idStudent() == selectedId).findFirst()
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
