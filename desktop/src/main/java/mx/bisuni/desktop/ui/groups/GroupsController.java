package mx.bisuni.desktop.ui.groups;

import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import mx.bisuni.desktop.AppContext;
import mx.bisuni.desktop.dao.GroupDao;
import mx.bisuni.desktop.model.Catalog;
import mx.bisuni.desktop.model.GroupRow;
import mx.bisuni.desktop.model.Period;
import mx.bisuni.desktop.model.PeriodStatus;
import mx.bisuni.desktop.model.RecordStatus;
import mx.bisuni.desktop.session.UserSession;
import mx.bisuni.desktop.ui.Async;
import mx.bisuni.desktop.ui.Dialogs;
import mx.bisuni.desktop.ui.ListSupport;
import mx.bisuni.desktop.ui.Option;
import mx.bisuni.desktop.ui.shell.ShellController;

import java.util.List;
import java.util.stream.IntStream;

public class GroupsController implements ShellController.ContentView {

    /** Catálogos para el formulario de grupo y el filtro de periodo. */
    record Catalogs(List<Catalog.Career> careers, List<Catalog.Teacher> teachers, List<Period> periods) {
    }

    @FXML private VBox root;
    @FXML private TextField searchField;
    @FXML private ComboBox<Option<Period>> periodCombo;
    @FXML private ComboBox<Option<Catalog.Career>> careerCombo;
    @FXML private ComboBox<Option<Integer>> semesterCombo;
    @FXML private ComboBox<Option<RecordStatus>> statusCombo;
    @FXML private Label countLabel;
    @FXML private TableView<GroupRow> table;
    @FXML private Label placeholderLabel;
    @FXML private TableColumn<GroupRow, String> nameCol;
    @FXML private TableColumn<GroupRow, String> periodCol;
    @FXML private TableColumn<GroupRow, String> careerCol;
    @FXML private TableColumn<GroupRow, Integer> semesterCol;
    @FXML private TableColumn<GroupRow, String> tutorCol;
    @FXML private TableColumn<GroupRow, Integer> studentsCol;
    @FXML private TableColumn<GroupRow, Integer> subjectsCol;
    @FXML private TableColumn<GroupRow, Integer> unassignedCol;
    @FXML private TableColumn<GroupRow, RecordStatus> statusCol;
    @FXML private Button editButton;
    @FXML private Button manageButton;
    @FXML private Button promoteButton;

    private AppContext ctx;
    private UserSession session;
    private Catalogs catalogs;
    private int loadSeq;

    @FXML
    private void initialize() {
        ListSupport.text(nameCol, GroupRow::name);
        ListSupport.text(periodCol, GroupRow::periodName);
        ListSupport.text(careerCol, GroupRow::careerName);
        ListSupport.value(semesterCol, GroupRow::semester);
        ListSupport.text(tutorCol, g -> g.tutorName() == null ? "Sin tutor" : g.tutorName());
        ListSupport.value(studentsCol, GroupRow::studentCount);
        ListSupport.value(subjectsCol, GroupRow::subjectCount);
        ListSupport.value(unassignedCol, GroupRow::unassignedCount);
        // Resalta los grupos con materias sin profesor: no se pueden agendar en el horario.
        unassignedCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Integer n, boolean empty) {
                super.updateItem(n, empty);
                setText(empty || n == null ? null : n.toString());
                getStyleClass().remove("cell-warning");
                if (!empty && n != null && n > 0) {
                    getStyleClass().add("cell-warning");
                }
            }
        });
        ListSupport.value(statusCol, GroupRow::status);
        ListSupport.onDoubleClick(table, this::manage);

        semesterCombo.getItems().setAll(Option.withAll("Todos", IntStream.rangeClosed(1, 12).boxed().toList()));
        semesterCombo.getSelectionModel().selectFirst();
        statusCombo.getItems().setAll(Option.withAll("Todos", List.of(RecordStatus.values())));
        statusCombo.getSelectionModel().selectFirst();
        var noSelection = table.getSelectionModel().selectedItemProperty().isNull();
        editButton.disableProperty().bind(noSelection);
        manageButton.disableProperty().bind(noSelection);
        // Solo los grupos de un periodo cerrado se reinscriben (o egresan).
        promoteButton.setDisable(true);
        table.getSelectionModel().selectedItemProperty().addListener(
                (o, a, g) -> promoteButton.setDisable(g == null || !g.isHistory()));
    }

    public void init(AppContext ctx, UserSession session) {
        this.ctx = ctx;
        this.session = session;
        Async.run(() -> new Catalogs(ctx.catalog().careers(), ctx.catalog().teachers(), ctx.periods().list()),
                c -> {
                    catalogs = c;
                    // Por omisión el periodo activo; los grupos de periodos cerrados son historial.
                    periodCombo.getItems().setAll(Option.withAll("Todos", c.periods()));
                    periodCombo.getItems().stream()
                            .filter(o -> o.value() != null && o.value().status() == PeriodStatus.Active)
                            .findFirst().ifPresentOrElse(o -> periodCombo.getSelectionModel().select(o),
                                    () -> periodCombo.getSelectionModel().selectFirst());
                    periodCombo.valueProperty().addListener((o, a, b) -> load());
                    careerCombo.getItems().setAll(Option.withAll("Todas", c.careers()));
                    careerCombo.getSelectionModel().selectFirst();
                    careerCombo.valueProperty().addListener((o, a, b) -> load());
                    semesterCombo.valueProperty().addListener((o, a, b) -> load());
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

    @FXML
    private void onNew() {
        if (catalogs != null && GroupForm.show(root, ctx, session, catalogs, null)) {
            load();
        }
    }

    @FXML
    private void onEdit() {
        GroupRow selected = table.getSelectionModel().getSelectedItem();
        if (selected != null && catalogs != null && GroupForm.show(root, ctx, session, catalogs, selected)) {
            load();
        }
    }

    @FXML
    private void onManage() {
        GroupRow selected = table.getSelectionModel().getSelectedItem();
        if (selected != null) {
            manage(selected);
        }
    }

    @FXML
    private void onPromote() {
        GroupRow selected = table.getSelectionModel().getSelectedItem();
        if (selected != null && catalogs != null) {
            PromoteForm.show(root, ctx, session, selected, catalogs.periods(), this::reloadPeriods);
        }
    }

    /** Tras reinscribir puede haber grupos nuevos en otro periodo: se recargan conteos y lista. */
    private void reloadPeriods() {
        Async.run(() -> ctx.periods().list(), list -> {
            catalogs = new Catalogs(catalogs.careers(), catalogs.teachers(), list);
            load();
        }, error -> Dialogs.error(root, error));
    }

    private void manage(GroupRow row) {
        GroupManageDialog.show(root, ctx, session, row);
        load(); // los conteos de materias, alumnos y "sin profesor" pudieron cambiar
    }

    private void load() {
        Catalog.Career career = careerCombo.getValue() == null ? null : careerCombo.getValue().value();
        Period period = periodCombo.getValue() == null ? null : periodCombo.getValue().value();
        var filter = new GroupDao.Filter(
                period == null ? null : period.idPeriod(),
                career == null ? null : career.idCareer(),
                semesterCombo.getValue() == null ? null : semesterCombo.getValue().value(),
                statusCombo.getValue() == null ? null : statusCombo.getValue().value(),
                searchField.getText());
        Integer selectedId = table.getSelectionModel().getSelectedItem() == null
                ? null : table.getSelectionModel().getSelectedItem().idGroup();
        int seq = ++loadSeq;
        placeholderLabel.setText("Cargando…");
        Async.run(() -> ctx.groups().list(filter),
                rows -> {
                    if (seq != loadSeq) {
                        return;
                    }
                    table.getItems().setAll(rows);
                    placeholderLabel.setText("No hay grupos con estos filtros.");
                    countLabel.setText(ListSupport.count(rows.size(), "grupo", "grupos"));
                    if (selectedId != null) {
                        rows.stream().filter(r -> r.idGroup() == selectedId).findFirst()
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
