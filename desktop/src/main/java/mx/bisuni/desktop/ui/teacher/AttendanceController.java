package mx.bisuni.desktop.ui.teacher;

import javafx.beans.Observable;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.css.PseudoClass;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import mx.bisuni.desktop.AppContext;
import mx.bisuni.desktop.model.AttendanceRow;
import mx.bisuni.desktop.model.AttendanceStatus;
import mx.bisuni.desktop.model.TeacherClass;
import mx.bisuni.desktop.model.Weekday;
import mx.bisuni.desktop.service.AttendanceService;
import mx.bisuni.desktop.session.UserSession;
import mx.bisuni.desktop.ui.Async;
import mx.bisuni.desktop.ui.Dialogs;
import mx.bisuni.desktop.ui.ListSupport;
import mx.bisuni.desktop.ui.shell.ShellController;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Pase de lista de una clase en una fecha. */
public class AttendanceController implements ShellController.ContentView {

    private static final PseudoClass MODIFIED = PseudoClass.getPseudoClass("modified");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM",
            Locale.forLanguageTag("es-MX"));

    /** Renglón editable: la marca original y la actual. */
    static final class Item {
        final AttendanceRow row;
        final ObjectProperty<AttendanceStatus> status;
        final BooleanProperty modified = new SimpleBooleanProperty(false);

        Item(AttendanceRow row) {
            this.row = row;
            this.status = new SimpleObjectProperty<>(row.status());
            status.addListener((o, a, b) -> modified.set(b != row.status()));
        }
    }

    @FXML private VBox root;
    @FXML private ComboBox<TeacherClass> classCombo;
    @FXML private DatePicker datePicker;
    @FXML private Label dayWarning;
    @FXML private Button allPresentButton;
    @FXML private TableView<Item> table;
    @FXML private Label placeholderLabel;
    @FXML private TableColumn<Item, String> nameCol;
    @FXML private TableColumn<Item, String> enrollmentCol;
    @FXML private TableColumn<Item, AttendanceStatus> statusCol;
    @FXML private TableColumn<Item, String> absencesCol;
    @FXML private Label summaryLabel;
    @FXML private Button discardButton;
    @FXML private Button saveButton;

    private final ObservableList<Item> items = FXCollections.observableArrayList(
            i -> new Observable[]{i.status, i.modified});
    private final BooleanProperty busy = new SimpleBooleanProperty(false);

    private AppContext ctx;
    private UserSession session;
    private TeacherClass appliedClass;
    private LocalDate appliedDate;
    private Set<Weekday> classDays = EnumSet.noneOf(Weekday.class);
    private boolean reverting;
    private int loadSeq;

    @FXML
    private void initialize() {
        ListSupport.text(nameCol, i -> i.row.fullName());
        ListSupport.text(enrollmentCol, i -> i.row.enrollmentNumber());
        statusCol.setCellValueFactory(c -> c.getValue().status);
        statusCol.setCellFactory(c -> new StatusCell());
        ListSupport.text(absencesCol, i -> i.row.sessions() == 0 ? "Sin registros"
                : i.row.absences() + " de " + i.row.sessions() + " (" + i.row.absencePct() + " %)");
        absencesCol.setCellFactory(c -> new TableCell<>() {
            @Override
            protected void updateItem(String text, boolean empty) {
                super.updateItem(text, empty);
                setText(empty ? null : text);
                getStyleClass().remove("risk");
                Item item = empty || getTableRow() == null ? null : getTableRow().getItem();
                if (item != null && AttendanceService.atRisk(item.row.absencePct())) {
                    getStyleClass().add("risk");
                }
            }
        });
        table.setRowFactory(tv -> new TableRow<>() {
            private final ChangeListener<Boolean> onModified = (o, a, b) -> pseudoClassStateChanged(MODIFIED, b);
            private Item watched;

            @Override
            protected void updateItem(Item item, boolean empty) {
                super.updateItem(item, empty);
                if (watched != null) {
                    watched.modified.removeListener(onModified);
                }
                watched = empty ? null : item;
                if (watched != null) {
                    watched.modified.addListener(onModified);
                }
                pseudoClassStateChanged(MODIFIED, watched != null && watched.modified.get());
            }
        });
        table.setItems(items);
        mx.bisuni.desktop.ui.DatePickers.spanish(datePicker);

        var modifiedCount = Bindings.createIntegerBinding(
                () -> (int) items.stream().filter(i -> i.modified.get()).count(), items);
        var unmarked = Bindings.createIntegerBinding(
                () -> (int) items.stream().filter(i -> i.status.get() == null).count(), items);
        summaryLabel.textProperty().bind(Bindings.createStringBinding(() -> {
            if (items.isEmpty()) {
                return "";
            }
            long absent = items.stream().filter(i -> i.status.get() == AttendanceStatus.Absent).count();
            String base = absent + (absent == 1 ? " falta" : " faltas");
            if (unmarked.get() > 0) {
                base += " · " + unmarked.get() + " sin marcar";
            }
            return modifiedCount.get() > 0 ? base + " · cambios sin guardar" : base;
        }, items));
        saveButton.disableProperty().bind(modifiedCount.isEqualTo(0).or(busy));
        discardButton.disableProperty().bind(modifiedCount.isEqualTo(0).or(busy));
        allPresentButton.disableProperty().bind(busy.or(Bindings.isEmpty(items)));
        table.disableProperty().bind(busy);
    }

    public void init(AppContext ctx, UserSession session) {
        this.ctx = ctx;
        this.session = session;
        Async.run(() -> ctx.teaching().myClasses(session),
                classes -> {
                    classCombo.getItems().setAll(classes);
                    if (classes.isEmpty()) {
                        placeholderLabel.setText("No tienes clases en el periodo activo.");
                        return;
                    }
                    classCombo.getSelectionModel().selectFirst();
                    appliedClass = classes.getFirst();
                    classCombo.valueProperty().addListener((o, old, sel) -> onSelectionChanged());
                    datePicker.valueProperty().addListener((o, old, sel) -> onSelectionChanged());
                    loadClassDays(true);
                },
                error -> Dialogs.error(root, error));
    }

    @Override
    public Node root() {
        return root;
    }

    @Override
    public boolean canLeave() {
        return !hasChanges() || Dialogs.confirm(root, "Cambios sin guardar",
                "Hay asistencia sin guardar. Si sales, se perderá.", "Salir sin guardar");
    }

    private boolean hasChanges() {
        return items.stream().anyMatch(i -> i.modified.get());
    }

    /** Cambió la clase o la fecha: confirma si hay cambios y recarga. */
    private void onSelectionChanged() {
        if (reverting) {
            return;
        }
        TeacherClass cls = classCombo.getValue();
        LocalDate date = datePicker.getValue();
        if (cls == null || date == null || (cls.equals(appliedClass) && date.equals(appliedDate))) {
            return;
        }
        if (hasChanges() && !Dialogs.confirm(root, "Cambios sin guardar",
                "Hay asistencia sin guardar. Si cambias de clase o de fecha, se perderá.", "Continuar")) {
            reverting = true;
            classCombo.setValue(appliedClass);
            datePicker.setValue(appliedDate);
            reverting = false;
            return;
        }
        boolean classChanged = !cls.equals(appliedClass);
        appliedClass = cls;
        if (classChanged) {
            loadClassDays(false);
        } else {
            appliedDate = date;
            load();
        }
    }

    /** Días con clase de la materia; al abrir la vista sugiere hoy o el último día con clase. */
    private void loadClassDays(boolean suggestDate) {
        TeacherClass cls = appliedClass;
        Async.run(() -> ctx.attendance().scheduledDays(cls.idGroup(), cls.idSubject()),
                days -> {
                    classDays = days;
                    if (suggestDate || datePicker.getValue() == null) {
                        reverting = true;
                        datePicker.setValue(AttendanceService.lastClassDay(LocalDate.now(), days).orElse(LocalDate.now()));
                        reverting = false;
                    }
                    appliedDate = datePicker.getValue();
                    load();
                },
                error -> Dialogs.error(root, error));
    }

    private void updateWarning() {
        LocalDate d = appliedDate;
        if (d == null) {
            dayWarning.setText("");
        } else if (d.isAfter(LocalDate.now())) {
            dayWarning.setText("Es una fecha futura: no se puede guardar.");
        } else if (!AttendanceService.isClassDay(d, classDays)) {
            dayWarning.setText("El " + DAY.format(d) + " no hay clase de esta materia en el horario.");
        } else {
            dayWarning.setText("");
        }
    }

    @FXML
    private void onAllPresent() {
        items.forEach(i -> i.status.set(AttendanceStatus.Present));
    }

    @FXML
    private void onDiscard() {
        items.forEach(i -> i.status.set(i.row.status()));
    }

    @FXML
    private void onSave() {
        Map<Integer, AttendanceStatus> marks = new LinkedHashMap<>();
        for (Item i : items) {
            if (i.modified.get() && i.status.get() != null) {
                marks.put(i.row.idStudent(), i.status.get());
            }
        }
        TeacherClass cls = appliedClass;
        LocalDate date = appliedDate;
        busy.set(true);
        Async.run(() -> ctx.attendance().save(cls.idGroup(), cls.idSubject(), date, marks, session),
                saved -> {
                    busy.set(false);
                    load();
                },
                error -> {
                    busy.set(false);
                    Dialogs.error(root, error);
                });
    }

    private void load() {
        TeacherClass cls = appliedClass;
        LocalDate date = appliedDate;
        if (cls == null || date == null) {
            return;
        }
        updateWarning();
        int seq = ++loadSeq;
        busy.set(true);
        placeholderLabel.setText("Cargando…");
        Async.run(() -> ctx.attendance().sheet(cls.idGroup(), cls.idSubject(), date, session),
                rows -> {
                    if (seq != loadSeq) {
                        return;
                    }
                    items.setAll(rows.stream().map(Item::new).toList());
                    placeholderLabel.setText("El grupo no tiene alumnos.");
                    busy.set(false);
                },
                error -> {
                    if (seq != loadSeq) {
                        return;
                    }
                    items.clear();
                    placeholderLabel.setText("No se pudo cargar la lista.");
                    busy.set(false);
                    Dialogs.error(root, error);
                });
    }

    /** Botones segmentados Presente / Falta / Retardo / Justificada ligados a la marca del renglón. */
    private final class StatusCell extends TableCell<Item, AttendanceStatus> {
        private final ToggleGroup group = new ToggleGroup();
        private final HBox box = new HBox();
        private ObjectProperty<AttendanceStatus> bound;
        private boolean syncing;

        StatusCell() {
            box.getStyleClass().add("segmented");
            for (AttendanceStatus s : AttendanceStatus.values()) {
                ToggleButton b = new ToggleButton(s.label());
                b.setUserData(s);
                b.setToggleGroup(group);
                b.setFocusTraversable(false);
                box.getChildren().add(b);
            }
            group.selectedToggleProperty().addListener((o, a, t) -> {
                if (!syncing && bound != null) {
                    bound.set(t == null ? null : (AttendanceStatus) t.getUserData());
                }
            });
        }

        private final ChangeListener<AttendanceStatus> sync = (o, a, s) -> select(s);

        private void select(AttendanceStatus s) {
            syncing = true;
            group.getToggles().forEach(t -> t.setSelected(t.getUserData() == s));
            syncing = false;
        }

        @Override
        protected void updateItem(AttendanceStatus value, boolean empty) {
            super.updateItem(value, empty);
            if (bound != null) {
                bound.removeListener(sync);
                bound = null;
            }
            Item item = empty || getTableRow() == null ? null : getTableRow().getItem();
            if (item == null) {
                setGraphic(null);
                return;
            }
            bound = item.status;
            bound.addListener(sync);
            select(bound.get());
            setGraphic(box);
        }
    }

    /** Para la prueba de humo. */
    public int shownRows() {
        return items.size();
    }
}
