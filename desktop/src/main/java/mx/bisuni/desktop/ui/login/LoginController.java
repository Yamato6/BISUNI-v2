package mx.bisuni.desktop.ui.login;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import mx.bisuni.desktop.service.ServiceException;
import mx.bisuni.desktop.ui.Async;
import mx.bisuni.desktop.ui.ChangePasswordDialog;
import mx.bisuni.desktop.ui.Navigator;

import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;

public class LoginController {

    private static final Logger LOG = Logger.getLogger(LoginController.class.getName());

    @FXML private TextField emailField;
    @FXML private PasswordField passwordField;
    @FXML private Button loginButton;
    @FXML private Label messageLabel;

    private Navigator navigator;

    public void init(Navigator navigator) {
        this.navigator = navigator;
        if (!navigator.context().isConfigured()) {
            showError(navigator.context().configError());
            loginButton.setDisable(true);
        }
        Platform.runLater(emailField::requestFocus);
    }

    @FXML
    private void onLogin() {
        String email = emailField.getText();
        char[] password = passwordField.getText().toCharArray();
        setBusy(true);
        messageLabel.setText("");
        Async.run(
                () -> navigator.context().auth().login(email, password),
                session -> {
                    if (session.mustChangePassword()) {
                        // Contraseña temporal: hay que crear una propia antes de entrar.
                        var changed = ChangePasswordDialog.show(loginButton, navigator.context().auth(), session, password);
                        Arrays.fill(password, '\0');
                        if (changed.isEmpty()) {
                            setBusy(false);
                            passwordField.clear();
                            showError("Debes crear tu propia contraseña para entrar.");
                            return;
                        }
                        navigator.showShell(changed.get());
                        return;
                    }
                    Arrays.fill(password, '\0');
                    navigator.showShell(session);
                },
                error -> {
                    Arrays.fill(password, '\0');
                    setBusy(false);
                    if (error instanceof ServiceException se) {
                        if (se.getCause() != null) {
                            LOG.log(Level.WARNING, se.getMessage(), se.getCause());
                        }
                        showError(se.getMessage());
                    } else {
                        LOG.log(Level.SEVERE, "Error inesperado al iniciar sesión", error);
                        showError("Ocurrió un error inesperado: " + error.getMessage());
                    }
                    passwordField.clear();
                    passwordField.requestFocus();
                });
    }

    private void setBusy(boolean busy) {
        loginButton.setDisable(busy);
        loginButton.setText(busy ? "Verificando…" : "Entrar");
        emailField.setDisable(busy);
        passwordField.setDisable(busy);
    }

    private void showError(String message) {
        messageLabel.setText(message);
    }
}
