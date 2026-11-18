package mx.bisuni.desktop.ui.announcements;

import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import mx.bisuni.desktop.AppContext;
import mx.bisuni.desktop.model.AnnouncementAudience;
import mx.bisuni.desktop.model.AnnouncementData;
import mx.bisuni.desktop.model.AnnouncementRow;
import mx.bisuni.desktop.model.Catalog;
import mx.bisuni.desktop.service.AnnouncementService;
import mx.bisuni.desktop.service.ServiceException;
import mx.bisuni.desktop.session.UserSession;
import mx.bisuni.desktop.ui.Async;
import mx.bisuni.desktop.ui.Dialogs;
import mx.bisuni.desktop.ui.FormDialog;
import mx.bisuni.desktop.ui.ListSupport;
import mx.bisuni.desktop.ui.Option;
import mx.bisuni.desktop.ui.shell.ShellController;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/** Avisos: el admin publica a toda la escuela, una carrera o un grupo; el profesor, a sus grupos. */
public class AnnouncementsController implements ShellController.ContentView {

    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("d MMM yyyy HH:mm",
            Locale.forLanguageTag("es-MX"));
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMM yyyy",
            Locale.forLanguageTag("es-MX"));

    @FXML private VBox root;
    @FXML private Label subtitleLabel;
    @FXML private CheckBox showExpired;
    @FXML private Label countLabel;
    @FXML private TableView<AnnouncementRow> table;
    @FXML private Label placeholderLabel;
    @FXML private TableColumn<AnnouncementRow, OffsetDateTime> publishedCol;
    @FXML private TableColumn<AnnouncementRow, String> titleCol;
    @FXML private TableColumn<AnnouncementRow, String> audienceCol;
    @FXML private TableColumn<AnnouncementRow, String> authorCol;
    @FXML private TableColumn<AnnouncementRow, String> expiresCol;
    @FXML private TextArea bodyArea;
    @FXML private Button deleteButton;

    private AppContext ctx;
    private UserSession session;
    private List<AnnouncementRow> all = List.of();
    private int loadSeq;

    @FXML
    private void initialize() {
        ListSupport.formatted(publishedCol, AnnouncementRow::publishedAt, WHEN::format);
        ListSupport.text(titleCol, AnnouncementRow::title);
        ListSupport.text(audienceCol, AnnouncementRow::audienceText);
        ListSupport.text(authorCol, AnnouncementRow::authorName);
        ListSupport.text(expiresCol, a -> a.expiresAt() == null ? "No vence"
                : a.expired() ? "Vencido" : DAY.format(a.expiresAt().minusSeconds(1)));
        table.getSelectionModel().selectedItemProperty().addListener((o, a, s) ->
                bodyArea.setText(s == null ? "" : s.title() + "\n\n" + s.body()));
        deleteButton.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
        showExpired.selectedProperty().addListener((o, a, b) -> show());
    }

    public void init(AppContext ctx, UserSession session) {
        this.ctx = ctx;
        this.session = session;
        subtitleLabel.setText(session.isAdmin()
                ? "Avisos para toda la escuela, una carrera o un grupo. Aparecen en el portal de los alumnos hasta que vencen."
                : "Avisos para tus grupos del periodo activo. Aparecen en el portal de tus alumnos hasta que vencen.");
        load();
    }

    @Override
    public Node root() {
        return root;
    }

    @FXML
    private void onNew() {
        Async.run(() -> session.isAdmin()
                        ? new Targets(ctx.catalog().careers(), ctx.catalog().groups().stream()
                        .map(g -> new Option<>(g.name() + " (" + g.semester() + "° sem.)", g.idGroup())).toList())
                        : new Targets(List.of(), ctx.announcements().teacherGroups(session).stream()
                        .map(g -> new Option<>(g.name() + (g.tutor() ? " (tutor)" : ""), g.idGroup())).toList()),
                targets -> {
                    if (!session.isAdmin() && targets.groups().isEmpty()) {
                        Dialogs.info(root, "Avisos", "No tienes grupos en el periodo activo.");
                        return;
                    }
                    if (showForm(targets)) {
                        load();
                    }
                },
                error -> Dialogs.error(root, error));
    }

    private record Targets(List<Catalog.Career> careers, List<Option<Integer>> groups) {
    }

    private boolean showForm(Targets targets) {
        FormDialog form = new FormDialog(root, "Nuevo aviso", "Publicar");
        ComboBox<AnnouncementAudience> audience = form.add("Para", new ComboBox<>());
        ComboBox<Catalog.Career> career = form.add("Carrera", new ComboBox<>());
        ComboBox<Option<Integer>> group = form.add("Grupo", new ComboBox<>());
        TextField title = form.add("Título", new TextField());
        TextArea body = form.add("Texto", new TextArea());
        body.setWrapText(true);
        body.setPrefRowCount(6);
        DatePicker expires = form.add("Vence", new DatePicker());
        expires.setPromptText("Opcional");
        mx.bisuni.desktop.ui.DatePickers.spanish(expires);
        form.addFull(FormDialog.note("El aviso se muestra hasta el día de vencimiento (inclusive). Sin fecha, no vence."));

        career.getItems().setAll(targets.careers());
        group.getItems().setAll(targets.groups());
        if (session.isAdmin()) {
            audience.getItems().setAll(AnnouncementAudience.values());
        } else {
            audience.getItems().setAll(AnnouncementAudience.Group);
        }
        audience.valueProperty().addListener((o, a, v) -> {
            career.setDisable(v != AnnouncementAudience.Career);
            group.setDisable(v != AnnouncementAudience.Group);
        });
        audience.setValue(session.isAdmin() ? AnnouncementAudience.All : AnnouncementAudience.Group);
        if (group.getItems().size() == 1) {
            group.getSelectionModel().selectFirst();
        }
        return form.showAndSave(() -> {
            var a = audience.getValue();
            var data = new AnnouncementData(title.getText(), body.getText(), a,
                    a == AnnouncementAudience.Career && career.getValue() != null ? career.getValue().idCareer() : null,
                    a == AnnouncementAudience.Group && group.getValue() != null ? group.getValue().value() : null,
                    expires.getValue());
            if (title.getText().length() > AnnouncementService.MAX_TITLE) {
                throw new ServiceException("El título admite máximo " + AnnouncementService.MAX_TITLE + " caracteres.");
            }
            return () -> ctx.announcements().publish(data, session);
        });
    }

    @FXML
    private void onDelete() {
        AnnouncementRow a = table.getSelectionModel().getSelectedItem();
        if (a != null && Dialogs.confirm(root, "Borrar aviso", "¿Borrar el aviso «" + a.title()
                + "»? Dejará de verse en el portal.", "Borrar")) {
            Async.run(() -> {
                        ctx.announcements().delete(a.idAnnouncement(), session);
                        return null;
                    },
                    r -> load(),
                    error -> Dialogs.error(root, error));
        }
    }

    private void load() {
        int seq = ++loadSeq;
        placeholderLabel.setText("Cargando…");
        Async.run(() -> ctx.announcements().list(session),
                rows -> {
                    if (seq != loadSeq) {
                        return;
                    }
                    all = rows;
                    show();
                },
                error -> {
                    if (seq != loadSeq) {
                        return;
                    }
                    placeholderLabel.setText("No se pudieron cargar los avisos.");
                    Dialogs.error(root, error);
                });
    }

    private void show() {
        var rows = showExpired.isSelected() ? all : all.stream().filter(a -> !a.expired()).toList();
        table.getItems().setAll(rows);
        placeholderLabel.setText(all.isEmpty() ? "Todavía no hay avisos." : "No hay avisos vigentes.");
        countLabel.setText(ListSupport.count(rows.size(), "aviso", "avisos"));
    }
}
