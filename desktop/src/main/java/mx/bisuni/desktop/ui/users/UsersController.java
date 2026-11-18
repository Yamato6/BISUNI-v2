package mx.bisuni.desktop.ui.users;

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
import mx.bisuni.desktop.model.RecordStatus;
import mx.bisuni.desktop.model.Role;
import mx.bisuni.desktop.model.UserRow;
import mx.bisuni.desktop.session.UserSession;
import mx.bisuni.desktop.ui.Async;
import mx.bisuni.desktop.ui.Dialogs;
import mx.bisuni.desktop.ui.ListSupport;
import mx.bisuni.desktop.ui.Option;
import mx.bisuni.desktop.ui.shell.ShellController;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class UsersController implements ShellController.ContentView {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy").withZone(ZoneId.systemDefault());

    @FXML private VBox root;
    @FXML private Button newButton;
    @FXML private Button editButton;
    @FXML private Button resetButton;
    @FXML private TextField searchField;
    @FXML private ComboBox<Option<Role>> roleCombo;
    @FXML private ComboBox<Option<RecordStatus>> statusCombo;
    @FXML private Label countLabel;
    @FXML private TableView<UserRow> table;
    @FXML private Label placeholderLabel;
    @FXML private TableColumn<UserRow, String> nameCol;
    @FXML private TableColumn<UserRow, String> usernameCol;
    @FXML private TableColumn<UserRow, String> emailCol;
    @FXML private TableColumn<UserRow, Role> roleCol;
    @FXML private TableColumn<UserRow, RecordStatus> statusCol;
    @FXML private TableColumn<UserRow, OffsetDateTime> createdCol;

    private AppContext ctx;
    private UserSession session;
    /** Evita que una respuesta vieja pise a una más nueva cuando el usuario escribe rápido. */
    private int loadSeq;

    @FXML
    private void initialize() {
        ListSupport.text(nameCol, u -> u.lastName() + ", " + u.firstName());
        ListSupport.text(usernameCol, UserRow::username);
        ListSupport.text(emailCol, UserRow::email);
        ListSupport.value(roleCol, UserRow::role);
        ListSupport.value(statusCol, UserRow::status);
        ListSupport.formatted(createdCol, UserRow::createdAt, d -> DATE.format(d));
        ListSupport.onDoubleClick(table, this::edit);

        roleCombo.getItems().setAll(Option.withAll("Todos", List.of(Role.values())));
        statusCombo.getItems().setAll(Option.withAll("Todos", List.of(RecordStatus.values())));
        roleCombo.getSelectionModel().selectFirst();
        statusCombo.getSelectionModel().selectFirst();

        editButton.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
        resetButton.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
    }

    public void init(AppContext ctx, UserSession session) {
        this.ctx = ctx;
        this.session = session;
        roleCombo.valueProperty().addListener((o, a, b) -> load());
        statusCombo.valueProperty().addListener((o, a, b) -> load());
        ListSupport.debounce(searchField, this::load);
        load();
    }

    @Override
    public Node root() {
        return root;
    }

    @FXML
    private void onNew() {
        UserForms.create(root, ctx, session, this::load);
    }

    /**
     * Genera una contraseña temporal y la muestra UNA vez para entregársela al usuario; al entrar
     * (escritorio o web) se le pedirá crear la suya.
     */
    @FXML
    private void onResetPassword() {
        UserRow selected = table.getSelectionModel().getSelectedItem();
        if (selected == null || !Dialogs.confirm(root, "Restablecer contraseña",
                "Se generará una contraseña temporal para " + selected.firstName() + " " + selected.lastName()
                        + ". Su contraseña actual dejará de funcionar y deberá crear una nueva al entrar. ¿Continuar?",
                "Restablecer")) {
            return;
        }
        Async.run(() -> ctx.users().resetPassword(selected.idUser(), session),
                temp -> TemporaryPasswordDialog.show(root, selected, temp),
                error -> Dialogs.error(root, error));
    }

    @FXML
    private void onEdit() {
        UserRow selected = table.getSelectionModel().getSelectedItem();
        if (selected != null) {
            edit(selected);
        }
    }

    private void edit(UserRow row) {
        if (UserForms.edit(root, ctx, session, row)) {
            load();
        }
    }

    private void load() {
        Role role = roleCombo.getValue() == null ? null : roleCombo.getValue().value();
        RecordStatus status = statusCombo.getValue() == null ? null : statusCombo.getValue().value();
        String search = searchField.getText();
        Integer selectedId = table.getSelectionModel().getSelectedItem() == null
                ? null : table.getSelectionModel().getSelectedItem().idUser();
        int seq = ++loadSeq;
        placeholderLabel.setText("Cargando…");
        Async.run(() -> ctx.users().list(role, status, search),
                rows -> {
                    if (seq != loadSeq) {
                        return;
                    }
                    table.getItems().setAll(rows);
                    placeholderLabel.setText("No hay usuarios con estos filtros.");
                    countLabel.setText(ListSupport.count(rows.size(), "usuario", "usuarios"));
                    if (selectedId != null) {
                        rows.stream().filter(r -> r.idUser() == selectedId).findFirst()
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
