package mx.bisuni.desktop.ui.audit;

import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import mx.bisuni.desktop.AppContext;
import mx.bisuni.desktop.dao.AuditDao;
import mx.bisuni.desktop.model.AuditRow;
import mx.bisuni.desktop.service.AuditService;
import mx.bisuni.desktop.session.UserSession;
import mx.bisuni.desktop.ui.Async;
import mx.bisuni.desktop.ui.Dialogs;
import mx.bisuni.desktop.ui.ListSupport;
import mx.bisuni.desktop.ui.Option;
import mx.bisuni.desktop.ui.shell.ShellController;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Consulta de la bitácora (solo admins). */
public class AuditController implements ShellController.ContentView {

    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("d MMM yyyy HH:mm:ss",
            Locale.forLanguageTag("es-MX"));

    @FXML private VBox root;
    @FXML private TextField searchField;
    @FXML private Label countLabel;
    @FXML private DatePicker fromPicker;
    @FXML private DatePicker toPicker;
    @FXML private ComboBox<Option<String>> entityCombo;
    @FXML private TableView<AuditRow> table;
    @FXML private Label placeholderLabel;
    @FXML private TableColumn<AuditRow, OffsetDateTime> atCol;
    @FXML private TableColumn<AuditRow, String> actorCol;
    @FXML private TableColumn<AuditRow, String> actionCol;
    @FXML private TableColumn<AuditRow, String> entityCol;
    @FXML private TableColumn<AuditRow, String> subjectCol;
    @FXML private TextArea detailArea;

    private AppContext ctx;
    private UserSession session;
    private int loadSeq;

    @FXML
    private void initialize() {
        ListSupport.formatted(atCol, AuditRow::at, t -> WHEN.format(t.atZoneSameInstant(ZoneId.systemDefault())));
        ListSupport.text(actorCol, r -> r.actorName() == null ? "(sin sesión)" : r.actorName());
        ListSupport.text(actionCol, r -> AuditService.actionLabel(r.action()));
        ListSupport.text(entityCol, r -> AuditService.entityLabel(r.entity()));
        ListSupport.text(subjectCol, r -> r.subject() == null ? "#" + r.entityId() : r.subject());
        table.getSelectionModel().selectedItemProperty().addListener((o, a, r) ->
                detailArea.setText(r == null ? "" : AuditService.describe(r)));

        List<Option<String>> entities = new ArrayList<>();
        entities.add(new Option<>("Todo", null));
        AuditService.ENTITIES.forEach((k, v) -> entities.add(new Option<>(v, k)));
        entityCombo.getItems().setAll(entities);
        entityCombo.getSelectionModel().selectFirst();
        mx.bisuni.desktop.ui.DatePickers.spanish(fromPicker, toPicker);
        fromPicker.setValue(LocalDate.now().minusDays(30));
        toPicker.setValue(LocalDate.now());
    }

    public void init(AppContext ctx, UserSession session) {
        this.ctx = ctx;
        this.session = session;
        fromPicker.valueProperty().addListener((o, a, b) -> load());
        toPicker.valueProperty().addListener((o, a, b) -> load());
        entityCombo.valueProperty().addListener((o, a, b) -> load());
        ListSupport.debounce(searchField, this::load);
        load();
    }

    @Override
    public Node root() {
        return root;
    }

    private void load() {
        var filter = new AuditDao.Filter(fromPicker.getValue(), toPicker.getValue(),
                entityCombo.getValue() == null ? null : entityCombo.getValue().value(), searchField.getText(),
                AuditService.MAX_ROWS);
        int seq = ++loadSeq;
        placeholderLabel.setText("Cargando…");
        Async.run(() -> ctx.audit().list(filter, session),
                rows -> {
                    if (seq != loadSeq) {
                        return;
                    }
                    table.getItems().setAll(rows);
                    placeholderLabel.setText("No hay cambios registrados con estos filtros.");
                    countLabel.setText(rows.size() >= AuditService.MAX_ROWS
                            ? "Mostrando los " + AuditService.MAX_ROWS + " más recientes"
                            : ListSupport.count(rows.size(), "cambio", "cambios"));
                },
                error -> {
                    if (seq != loadSeq) {
                        return;
                    }
                    table.getItems().clear();
                    placeholderLabel.setText("No se pudo consultar la bitácora.");
                    Dialogs.error(root, error);
                });
    }
}
