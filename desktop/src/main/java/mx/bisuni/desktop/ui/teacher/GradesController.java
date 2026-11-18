package mx.bisuni.desktop.ui.teacher;

import javafx.beans.Observable;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.css.PseudoClass;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import mx.bisuni.desktop.AppContext;
import mx.bisuni.desktop.model.GradeInput;
import mx.bisuni.desktop.model.TeacherClass;
import mx.bisuni.desktop.service.ServiceException;
import mx.bisuni.desktop.session.UserSession;
import mx.bisuni.desktop.ui.Async;
import mx.bisuni.desktop.ui.Dialogs;
import mx.bisuni.desktop.ui.ListSupport;
import mx.bisuni.desktop.ui.shell.ShellController;

import java.util.ArrayList;
import java.util.List;

/** Captura de calificaciones de una clase, en forma de hoja: todos los alumnos a la vez. */
public class GradesController implements ShellController.ContentView {

    private static final PseudoClass MODIFIED = PseudoClass.getPseudoClass("modified");
    private static final PseudoClass INVALID = PseudoClass.getPseudoClass("invalid");

    @FXML private VBox root;
    @FXML private ComboBox<TeacherClass> classCombo;
    @FXML private Label progressLabel;
    @FXML private TableView<GradeItem> table;
    @FXML private Label placeholderLabel;
    @FXML private TableColumn<GradeItem, String> nameCol;
    @FXML private TableColumn<GradeItem, String> usernameCol;
    @FXML private TableColumn<GradeItem, String> p1Col;
    @FXML private TableColumn<GradeItem, String> p2Col;
    @FXML private TableColumn<GradeItem, String> p3Col;
    @FXML private TableColumn<GradeItem, String> finalCol;
    @FXML private TableColumn<GradeItem, String> noteCol;
    @FXML private Label changesLabel;
    @FXML private Button discardButton;
    @FXML private Button saveButton;
    @FXML private Button closeActaButton;
    @FXML private Label actaLabel;

    private final ObservableList<GradeItem> items = FXCollections.observableArrayList(
            i -> new Observable[]{i.modifiedBinding(), i.invalidBinding(), i.finalPreview()});
    private final BooleanProperty busy = new SimpleBooleanProperty(false);
    /** Acta cerrada: la hoja es de solo lectura. */
    private final BooleanProperty readOnly = new SimpleBooleanProperty(false);

    private AppContext ctx;
    private UserSession session;
    private TeacherClass applied;
    private boolean revertingSelection;
    private int loadSeq;

    @FXML
    private void initialize() {
        ListSupport.text(nameCol, i -> i.row().fullName());
        ListSupport.text(usernameCol, i -> i.row().username());
        setUpGradeColumn(p1Col, 1);
        setUpGradeColumn(p2Col, 2);
        setUpGradeColumn(p3Col, 3);
        finalCol.setCellValueFactory(c -> c.getValue().finalPreview());
        finalCol.getStyleClass().add("grade-final");
        ListSupport.text(noteCol, i -> i.row().inGroup() ? "" : "Ya no está en el grupo");
        noteCol.getStyleClass().add("grade-note");

        table.setRowFactory(tv -> new TableRow<>() {
            private final ChangeListener<Boolean> onModified = (o, a, b) -> pseudoClassStateChanged(MODIFIED, b);
            private GradeItem watched;

            @Override
            protected void updateItem(GradeItem item, boolean empty) {
                super.updateItem(item, empty);
                if (watched != null) {
                    watched.modifiedBinding().removeListener(onModified);
                }
                watched = empty ? null : item;
                if (watched != null) {
                    watched.modifiedBinding().addListener(onModified);
                }
                pseudoClassStateChanged(MODIFIED, watched != null && watched.isModified());
            }
        });
        table.setItems(items);
        table.setEditable(false); // las celdas de parciales son campos de texto siempre activos

        var modifiedCount = Bindings.createIntegerBinding(
                () -> (int) items.stream().filter(GradeItem::isModified).count(), items);
        changesLabel.textProperty().bind(Bindings.createStringBinding(() -> switch (modifiedCount.get()) {
            case 0 -> "";
            case 1 -> "1 alumno con cambios sin guardar";
            default -> modifiedCount.get() + " alumnos con cambios sin guardar";
        }, modifiedCount));
        saveButton.disableProperty().bind(modifiedCount.isEqualTo(0).or(busy).or(readOnly));
        discardButton.disableProperty().bind(modifiedCount.isEqualTo(0).or(busy).or(readOnly));
        closeActaButton.disableProperty().bind(modifiedCount.greaterThan(0).or(busy).or(readOnly)
                .or(Bindings.isEmpty(items)));
        actaLabel.setText("Acta cerrada");
        actaLabel.visibleProperty().bind(readOnly);
        actaLabel.managedProperty().bind(readOnly);
        progressLabel.textProperty().bind(Bindings.createStringBinding(() -> {
            long complete = items.stream().filter(i -> !"—".equals(i.finalPreview().get())).count();
            return items.isEmpty() ? "" : complete + " de " + items.size() + " con calificación final";
        }, items));
    }

    /** Celda con un campo de texto siempre editable ligado al parcial del renglón (sin doble clic ni Enter). */
    private void setUpGradeColumn(TableColumn<GradeItem, String> col, int parcial) {
        col.setCellValueFactory(c -> c.getValue().parcial(parcial));
        col.setCellFactory(c -> new TableCell<>() {
            private final TextField field = new TextField();
            private StringProperty bound;
            private final ChangeListener<String> validate =
                    (o, a, text) -> field.pseudoClassStateChanged(INVALID, !GradeItem.valid(text));

            {
                field.getStyleClass().add("grade-field");
                field.setPrefColumnCount(4);
                field.textProperty().addListener(validate);
                field.disableProperty().bind(busy.or(readOnly));
            }

            @Override
            protected void updateItem(String value, boolean empty) {
                super.updateItem(value, empty);
                int index = getIndex();
                GradeItem item = empty || index < 0 || index >= getTableView().getItems().size()
                        ? null : getTableView().getItems().get(index);
                if (bound != null) {
                    field.textProperty().unbindBidirectional(bound);
                    bound = null;
                }
                if (item == null) {
                    setGraphic(null);
                    return;
                }
                bound = item.parcial(parcial);
                field.textProperty().bindBidirectional(bound);
                setGraphic(field);
            }
        });
    }

    public void init(AppContext ctx, UserSession session, TeacherClass preselect) {
        this.ctx = ctx;
        this.session = session;
        Async.run(() -> ctx.teaching().myClasses(session),
                classes -> {
                    classCombo.getItems().setAll(classes);
                    if (classes.isEmpty()) {
                        placeholderLabel.setText("No tienes clases asignadas.");
                        return;
                    }
                    TeacherClass first = preselect == null ? classes.getFirst() : classes.stream()
                            .filter(c -> c.idGroup() == preselect.idGroup() && c.idSubject() == preselect.idSubject())
                            .findFirst().orElse(classes.getFirst());
                    classCombo.getSelectionModel().select(first);
                    classCombo.valueProperty().addListener((o, old, sel) -> onClassChanged(old, sel));
                    applied = first;
                    load();
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
                "Hay calificaciones sin guardar. Si sales, se perderán.", "Salir sin guardar");
    }

    private boolean hasChanges() {
        return items.stream().anyMatch(GradeItem::isModified);
    }

    private void onClassChanged(TeacherClass old, TeacherClass selected) {
        if (revertingSelection || selected == null || selected.equals(applied)) {
            return;
        }
        if (hasChanges() && !Dialogs.confirm(root, "Cambios sin guardar",
                "Hay calificaciones sin guardar de " + applied.label() + ". Si cambias de clase, se perderán.",
                "Cambiar de clase")) {
            revertingSelection = true;
            classCombo.getSelectionModel().select(applied);
            revertingSelection = false;
            return;
        }
        applied = selected;
        load();
    }

    /** Cierra el acta: pide confirmación porque después solo un admin la puede reabrir. */
    @FXML
    private void onCloseActa() {
        TeacherClass cls = applied;
        if (cls == null || !Dialogs.confirm(root, "Cerrar acta", "¿Cerrar el acta de " + cls.label() + "? Las "
                + "calificaciones quedarán como definitivas y ya no podrás modificarlas; solo control escolar "
                + "puede reabrirla.", "Cerrar acta")) {
            return;
        }
        busy.set(true);
        Async.run(() -> {
                    ctx.grades().closeActa(cls.idGroup(), cls.idSubject(), session);
                    return null;
                },
                r -> {
                    busy.set(false);
                    TeacherClass closed = cls.withActaClosed();
                    int index = classCombo.getItems().indexOf(cls);
                    revertingSelection = true;
                    classCombo.getItems().set(index, closed);
                    classCombo.getSelectionModel().select(closed);
                    revertingSelection = false;
                    applied = closed;
                    readOnly.set(true);
                    Dialogs.info(root, "Acta cerrada", "Se cerró el acta de " + cls.label() + ".");
                },
                error -> {
                    busy.set(false);
                    Dialogs.error(root, error);
                });
    }

    @FXML
    private void onDiscard() {
        items.forEach(GradeItem::revert);
    }

    @FXML
    private void onSave() {
        List<GradeInput> changes = new ArrayList<>();
        try {
            for (GradeItem item : items) {
                if (item.isModified()) {
                    changes.add(item.toInput());
                }
            }
        } catch (ServiceException e) {
            Dialogs.error(root, e);
            return;
        }
        if (changes.isEmpty()) {
            return;
        }
        TeacherClass cls = applied;
        busy.set(true);
        Async.run(() -> ctx.grades().save(cls.idGroup(), cls.idSubject(), changes, session),
                saved -> {
                    busy.set(false);
                    Dialogs.info(root, "Calificaciones guardadas",
                            "Se guardaron las calificaciones de " + saved + (saved == 1 ? " alumno" : " alumnos")
                                    + " en " + cls.label() + ".");
                    load();
                },
                error -> {
                    busy.set(false);
                    Dialogs.error(root, error); // los cambios siguen en pantalla para corregirlos
                });
    }

    private void load() {
        TeacherClass cls = applied;
        if (cls == null) {
            return;
        }
        int seq = ++loadSeq;
        busy.set(true);
        readOnly.set(cls.actaClosed());
        placeholderLabel.setText("Cargando…");
        Async.run(() -> ctx.grades().sheet(cls.idGroup(), cls.idSubject(), session),
                rows -> {
                    if (seq != loadSeq) {
                        return;
                    }
                    items.setAll(rows.stream().map(GradeItem::new).toList());
                    placeholderLabel.setText("El grupo no tiene alumnos.");
                    busy.set(false);
                },
                error -> {
                    if (seq != loadSeq) {
                        return;
                    }
                    items.clear();
                    placeholderLabel.setText("No se pudieron cargar las calificaciones.");
                    busy.set(false);
                    Dialogs.error(root, error);
                });
    }

    /** Para la prueba de humo. */
    public int shownRows() {
        return items.size();
    }
}
