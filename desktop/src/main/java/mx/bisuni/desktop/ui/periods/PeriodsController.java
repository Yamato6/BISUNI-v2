package mx.bisuni.desktop.ui.periods;

import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import mx.bisuni.desktop.AppContext;
import mx.bisuni.desktop.model.Period;
import mx.bisuni.desktop.model.PeriodData;
import mx.bisuni.desktop.model.PeriodStatus;
import mx.bisuni.desktop.service.ServiceException;
import mx.bisuni.desktop.session.UserSession;
import mx.bisuni.desktop.ui.Async;
import mx.bisuni.desktop.ui.Dialogs;
import mx.bisuni.desktop.ui.FormDialog;
import mx.bisuni.desktop.ui.ListSupport;
import mx.bisuni.desktop.ui.shell.ShellController;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.concurrent.Callable;

public class PeriodsController implements ShellController.ContentView {

    static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.forLanguageTag("es-MX"));

    @FXML private VBox root;
    @FXML private Label activeLabel;
    @FXML private Label countLabel;
    @FXML private TableView<Period> table;
    @FXML private Label placeholderLabel;
    @FXML private TableColumn<Period, String> nameCol;
    @FXML private TableColumn<Period, LocalDate> startCol;
    @FXML private TableColumn<Period, LocalDate> endCol;
    @FXML private TableColumn<Period, PeriodStatus> statusCol;
    @FXML private TableColumn<Period, String> admissionsCol;
    @FXML private TableColumn<Period, Integer> groupsCol;
    @FXML private Button editButton;
    @FXML private Button admissionsButton;
    @FXML private Button activateButton;
    @FXML private Button closeButton;

    private AppContext ctx;
    private UserSession session;
    private int loadSeq;

    @FXML
    private void initialize() {
        ListSupport.text(nameCol, Period::name);
        ListSupport.formatted(startCol, Period::startDate, DATE::format);
        ListSupport.formatted(endCol, Period::endDate, DATE::format);
        ListSupport.value(statusCol, Period::status);
        ListSupport.text(admissionsCol, p -> p.admissionsOpen() ? "Abierta" : "Cerrada");
        ListSupport.value(groupsCol, Period::groupCount);
        ListSupport.onDoubleClick(table, p -> onEdit());
        table.getSelectionModel().selectedItemProperty().addListener((o, a, p) -> updateButtons(p));
        updateButtons(null);
    }

    public void init(AppContext ctx, UserSession session) {
        this.ctx = ctx;
        this.session = session;
        load();
    }

    @Override
    public Node root() {
        return root;
    }

    private void updateButtons(Period p) {
        boolean none = p == null;
        boolean closed = !none && p.isClosed();
        editButton.setDisable(none || closed);
        admissionsButton.setDisable(none || closed);
        admissionsButton.setText(!none && p.admissionsOpen() ? "Cerrar convocatoria" : "Abrir convocatoria");
        activateButton.setDisable(none || p.status() != PeriodStatus.Planned);
        closeButton.setDisable(none || p.status() != PeriodStatus.Active);
    }

    @FXML
    private void onNew() {
        if (showForm(null)) {
            load();
        }
    }

    @FXML
    private void onEdit() {
        Period p = table.getSelectionModel().getSelectedItem();
        if (p != null && !p.isClosed() && showForm(p)) {
            load();
        }
    }

    @FXML
    private void onToggleAdmissions() {
        Period p = table.getSelectionModel().getSelectedItem();
        if (p == null) {
            return;
        }
        boolean open = !p.admissionsOpen();
        run(() -> {
            ctx.periods().setAdmissionsOpen(p.idPeriod(), open, session);
            return null;
        }, open ? "La convocatoria " + p.name() + " está abierta: los aspirantes ya pueden registrarse en el portal."
                : "La convocatoria " + p.name() + " está cerrada.");
    }

    @FXML
    private void onActivate() {
        Period p = table.getSelectionModel().getSelectedItem();
        if (p == null) {
            return;
        }
        String current = table.getItems().stream().filter(x -> x.status() == PeriodStatus.Active)
                .map(Period::name).findFirst().orElse(null);
        String msg = "¿Activar el periodo " + p.name() + "?"
                + (current == null ? "" : " El periodo " + current + " se cerrará: sus calificaciones quedarán como "
                + "historial y sus grupos ya no se podrán modificar. Después, reinscribe a los alumnos desde Grupos.");
        if (Dialogs.confirm(root, "Activar periodo", msg, "Activar")) {
            run(() -> {
                ctx.periods().activate(p.idPeriod(), session);
                return null;
            }, "El periodo " + p.name() + " está activo.");
        }
    }

    @FXML
    private void onClose() {
        Period p = table.getSelectionModel().getSelectedItem();
        if (p == null) {
            return;
        }
        if (Dialogs.confirm(root, "Cerrar periodo", "¿Cerrar el periodo " + p.name() + "? Sus calificaciones "
                + "quedarán como historial y ya no se podrán modificar. No habrá periodo activo hasta que actives otro.",
                "Cerrar periodo")) {
            run(() -> {
                ctx.periods().close(p.idPeriod(), session);
                return null;
            }, "El periodo " + p.name() + " está cerrado.");
        }
    }

    private void run(Callable<?> work, String done) {
        Async.run(work, r -> {
            load();
            Dialogs.info(root, "Periodos", done);
        }, error -> Dialogs.error(root, error));
    }

    private boolean showForm(Period period) {
        boolean creating = period == null;
        FormDialog form = new FormDialog(root, creating ? "Nuevo periodo" : "Editar periodo",
                creating ? "Crear periodo" : "Guardar");
        TextField name = form.add("Nombre", new TextField(creating ? "" : period.name()));
        name.setPromptText("p. ej. 2027-1");
        DatePicker start = form.add("Inicio", new DatePicker(creating ? null : period.startDate()));
        DatePicker end = form.add("Fin", new DatePicker(creating ? null : period.endDate()));
        if (creating) {
            form.addFull(FormDialog.note("El periodo se crea planeado: puedes preparar sus grupos y abrir su "
                    + "convocatoria antes de activarlo."));
        }
        return form.showAndSave(() -> {
            if (start.getValue() == null || end.getValue() == null) {
                throw new ServiceException("Indica las fechas de inicio y fin.");
            }
            var data = new PeriodData(name.getText(), start.getValue(), end.getValue());
            return () -> {
                if (creating) {
                    ctx.periods().create(data, session);
                } else {
                    ctx.periods().update(period.idPeriod(), data, session);
                }
                return null;
            };
        });
    }

    private void load() {
        Integer selectedId = table.getSelectionModel().getSelectedItem() == null
                ? null : table.getSelectionModel().getSelectedItem().idPeriod();
        int seq = ++loadSeq;
        placeholderLabel.setText("Cargando…");
        Async.run(() -> ctx.periods().list(),
                rows -> {
                    if (seq != loadSeq) {
                        return;
                    }
                    table.getItems().setAll(rows);
                    placeholderLabel.setText("Todavía no hay periodos. Crea el primero.");
                    countLabel.setText(ListSupport.count(rows.size(), "periodo", "periodos"));
                    activeLabel.setText(rows.stream().filter(p -> p.status() == PeriodStatus.Active).findFirst()
                            .map(p -> "Periodo activo: " + p.name() + " (" + DATE.format(p.startDate()) + " – "
                                    + DATE.format(p.endDate()) + ")")
                            .orElse("No hay periodo activo: los profesores no pueden capturar calificaciones ni asistencia."));
                    if (selectedId != null) {
                        rows.stream().filter(r -> r.idPeriod() == selectedId).findFirst()
                                .ifPresent(r -> table.getSelectionModel().select(r));
                    }
                    updateButtons(table.getSelectionModel().getSelectedItem());
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
