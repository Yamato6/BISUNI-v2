package mx.bisuni.desktop.ui.admissions;

import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.css.PseudoClass;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.ComboBoxTableCell;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;
import mx.bisuni.desktop.model.AspirantRow;
import mx.bisuni.desktop.model.AspirantStatus;
import mx.bisuni.desktop.service.AdmissionService;
import mx.bisuni.desktop.service.AdmissionService.Change;
import mx.bisuni.desktop.session.UserSession;
import mx.bisuni.desktop.ui.Async;
import mx.bisuni.desktop.ui.Dialogs;
import mx.bisuni.desktop.ui.shell.ShellController;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class AdmissionsController implements ShellController.ContentView {

    /** Opción del filtro; {@code status == null} significa "Todos". */
    private record FilterOption(String label, AspirantStatus status) {
        @Override
        public String toString() {
            return label;
        }
    }

    private static final PseudoClass MODIFIED = PseudoClass.getPseudoClass("modified");
    private static final PseudoClass LOCKED = PseudoClass.getPseudoClass("locked");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    @FXML private VBox root;
    @FXML private ComboBox<FilterOption> filterCombo;
    @FXML private Button reloadButton;
    @FXML private TableView<AdmissionItem> table;
    @FXML private Label placeholderLabel;
    @FXML private TableColumn<AdmissionItem, String> folioCol;
    @FXML private TableColumn<AdmissionItem, String> usernameCol;
    @FXML private TableColumn<AdmissionItem, String> firstNameCol;
    @FXML private TableColumn<AdmissionItem, String> lastNameCol;
    @FXML private TableColumn<AdmissionItem, String> emailCol;
    @FXML private TableColumn<AdmissionItem, String> curpCol;
    @FXML private TableColumn<AdmissionItem, String> careerCol;
    @FXML private TableColumn<AdmissionItem, String> periodCol;
    @FXML private TableColumn<AdmissionItem, LocalDate> dateCol;
    @FXML private TableColumn<AdmissionItem, AspirantStatus> statusCol;
    @FXML private Label changesLabel;
    @FXML private Button discardButton;
    @FXML private Button saveButton;

    private final ObservableList<AdmissionItem> items =
            FXCollections.observableArrayList(i -> new javafx.beans.Observable[]{i.modifiedBinding()});

    private AdmissionService service;
    private UserSession session;
    private FilterOption appliedFilter;
    private boolean revertingFilter;
    private final BooleanProperty busy = new SimpleBooleanProperty(false);

    @FXML
    private void initialize() {
        setUpColumns();
        setUpRows();

        filterCombo.getItems().setAll(
                new FilterOption("Todos", null),
                new FilterOption("Pendientes", AspirantStatus.Pending),
                new FilterOption("Aceptados", AspirantStatus.Accepted),
                new FilterOption("Rechazados", AspirantStatus.Rejected));

        var modifiedCount = Bindings.createIntegerBinding(
                () -> (int) items.stream().filter(AdmissionItem::isModified).count(), items);
        changesLabel.textProperty().bind(Bindings.createStringBinding(() -> switch (modifiedCount.get()) {
            case 0 -> "";
            case 1 -> "1 cambio sin guardar";
            default -> modifiedCount.get() + " cambios sin guardar";
        }, modifiedCount));
        saveButton.disableProperty().bind(modifiedCount.isEqualTo(0).or(busy));
        discardButton.disableProperty().bind(modifiedCount.isEqualTo(0).or(busy));

        table.setItems(items);
    }

    public void init(AdmissionService service, UserSession session) {
        this.service = service;
        this.session = session;
        appliedFilter = filterCombo.getItems().get(1); // Pendientes: es lo que normalmente se revisa
        filterCombo.getSelectionModel().select(appliedFilter);
        filterCombo.valueProperty().addListener((obs, old, sel) -> onFilterChanged(old, sel));
        load();
    }

    @Override
    public Node root() {
        return root;
    }

    @Override
    public boolean canLeave() {
        return !hasChanges() || Dialogs.confirm(root, "Cambios sin guardar",
                "Hay cambios de estatus sin guardar. Si sales, se perderán.", "Salir sin guardar");
    }

    private boolean hasChanges() {
        return items.stream().anyMatch(AdmissionItem::isModified);
    }

    private void setUpColumns() {
        folioCol.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().row().folio()));
        usernameCol.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().row().username()));
        firstNameCol.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().row().firstName()));
        lastNameCol.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().row().lastName()));
        emailCol.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().row().email()));
        curpCol.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().row().curp()));
        careerCol.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().row().careerName()));
        periodCol.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().row().periodName()));
        dateCol.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().row().applicationDate()));
        dateCol.setCellFactory(col -> new javafx.scene.control.TableCell<>() {
            @Override
            protected void updateItem(LocalDate date, boolean empty) {
                super.updateItem(date, empty);
                setText(empty || date == null ? null : DATE.format(date));
            }
        });

        statusCol.setCellValueFactory(c -> c.getValue().statusProperty());
        StringConverter<AspirantStatus> converter = new StringConverter<>() {
            @Override
            public String toString(AspirantStatus s) {
                return s == null ? "" : s.label();
            }

            @Override
            public AspirantStatus fromString(String s) {
                throw new UnsupportedOperationException();
            }
        };
        statusCol.setCellFactory(col -> new ComboBoxTableCell<>(converter, AspirantStatus.values()) {
            @Override
            public void startEdit() {
                TableRow<AdmissionItem> row = getTableRow();
                if (busy.get() || row == null || row.getItem() == null || !row.getItem().isEditable()) {
                    return;
                }
                super.startEdit();
            }
        });
        // El valor se escribe directo en la propiedad del item.
        statusCol.setOnEditCommit(e -> e.getRowValue().statusProperty().set(e.getNewValue()));
    }

    private void setUpRows() {
        table.setRowFactory(tv -> new TableRow<>() {
            // Un solo listener por fila; se mueve al item que muestre la fila en cada momento.
            private final ChangeListener<Boolean> onModified =
                    (obs, was, is) -> pseudoClassStateChanged(MODIFIED, is);
            private AdmissionItem watched;

            @Override
            protected void updateItem(AdmissionItem item, boolean empty) {
                super.updateItem(item, empty);
                if (watched != item) {
                    if (watched != null) {
                        watched.modifiedBinding().removeListener(onModified);
                    }
                    watched = empty ? null : item;
                    if (watched != null) {
                        watched.modifiedBinding().addListener(onModified);
                    }
                }
                pseudoClassStateChanged(MODIFIED, watched != null && watched.isModified());
                pseudoClassStateChanged(LOCKED, watched != null && !watched.isEditable());
            }
        });
    }

    private void onFilterChanged(FilterOption old, FilterOption selected) {
        if (revertingFilter || selected == null || selected.equals(appliedFilter)) {
            return;
        }
        if (hasChanges() && !Dialogs.confirm(root, "Cambios sin guardar",
                "Hay cambios de estatus sin guardar. Si cambias el filtro, se perderán.", "Cambiar filtro")) {
            revertingFilter = true;
            filterCombo.getSelectionModel().select(appliedFilter);
            revertingFilter = false;
            return;
        }
        appliedFilter = selected;
        load();
    }

    @FXML
    private void onReload() {
        if (hasChanges() && !Dialogs.confirm(root, "Cambios sin guardar",
                "Hay cambios de estatus sin guardar. Si recargas, se perderán.", "Recargar")) {
            return;
        }
        load();
    }

    @FXML
    private void onDiscard() {
        items.forEach(AdmissionItem::revert);
    }

    @FXML
    private void onSave() {
        Map<Integer, Change> changes = new LinkedHashMap<>();
        int accept = 0, reject = 0, pending = 0;
        for (AdmissionItem item : items) {
            if (!item.isModified()) {
                continue;
            }
            AspirantStatus to = item.statusProperty().get();
            changes.put(item.row().idAspirant(), new Change(item.originalStatus(), to));
            switch (to) {
                case Accepted -> accept++;
                case Rejected -> reject++;
                case Pending -> pending++;
            }
        }
        if (changes.isEmpty()) {
            return;
        }
        StringBuilder msg = new StringBuilder("Se aplicarán estos cambios:\n");
        if (accept > 0) {
            msg.append("\n• ").append(accept).append(accept == 1 ? " aspirante aceptado" : " aspirantes aceptados")
                    .append(" (se inscribirán como alumnos; no se puede revertir)");
        }
        if (reject > 0) {
            msg.append("\n• ").append(reject).append(reject == 1 ? " aspirante rechazado" : " aspirantes rechazados");
        }
        if (pending > 0) {
            msg.append("\n• ").append(pending).append(pending == 1 ? " aspirante regresa" : " aspirantes regresan")
                    .append(" a pendiente");
        }
        msg.append("\n\n¿Continuar?");
        if (!Dialogs.confirm(root, "Guardar cambios", msg.toString(), "Guardar")) {
            return;
        }

        setBusy(true);
        Async.run(
                () -> service.applyChanges(changes, session),
                result -> {
                    setBusy(false);
                    Dialogs.info(root, "Cambios guardados", summary(result));
                    load();
                },
                error -> {
                    setBusy(false);
                    Dialogs.error(root, error);
                    load();
                });
    }

    private static String summary(AdmissionService.Result r) {
        StringBuilder sb = new StringBuilder("Se guardaron los cambios.");
        if (r.accepted() > 0) {
            sb.append("\nAceptados e inscritos: ").append(r.accepted());
        }
        if (r.rejected() > 0) {
            sb.append("\nRechazados: ").append(r.rejected());
        }
        if (r.pending() > 0) {
            sb.append("\nDe vuelta a pendiente: ").append(r.pending());
        }
        return sb.toString();
    }

    private void load() {
        AspirantStatus filter = appliedFilter == null ? null : appliedFilter.status();
        setBusy(true);
        placeholderLabel.setText("Cargando…");
        Async.run(
                () -> service.list(filter),
                (List<AspirantRow> rows) -> {
                    items.setAll(rows.stream().map(AdmissionItem::new).toList());
                    placeholderLabel.setText("No hay aspirantes con este filtro.");
                    setBusy(false);
                },
                error -> {
                    items.clear();
                    placeholderLabel.setText("No se pudo cargar la lista.");
                    setBusy(false);
                    Dialogs.error(root, error);
                });
    }

    private void setBusy(boolean busy) {
        this.busy.set(busy);
        filterCombo.setDisable(busy);
        reloadButton.setDisable(busy);
        table.setDisable(busy);
        if (busy) {
            table.edit(-1, null);
        }
    }
}
