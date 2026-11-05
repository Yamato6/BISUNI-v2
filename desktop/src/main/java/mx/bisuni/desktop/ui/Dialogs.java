package mx.bisuni.desktop.ui;

import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import mx.bisuni.desktop.service.ServiceException;

import java.util.logging.Level;
import java.util.logging.Logger;

/** Diálogos comunes en español. */
public final class Dialogs {

    private static final Logger LOG = Logger.getLogger(Dialogs.class.getName());

    private Dialogs() {
    }

    public static boolean confirm(Node owner, String title, String message, String okText) {
        ButtonType ok = new ButtonType(okText, ButtonBar.ButtonData.OK_DONE);
        ButtonType cancel = new ButtonType("Cancelar", ButtonBar.ButtonData.CANCEL_CLOSE);
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, message, ok, cancel);
        prepare(alert, owner, title);
        return alert.showAndWait().orElse(cancel) == ok;
    }

    public static void info(Node owner, String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION, message, new ButtonType("Aceptar", ButtonBar.ButtonData.OK_DONE));
        prepare(alert, owner, title);
        alert.showAndWait();
    }

    /** Muestra el mensaje de una {@link ServiceException}; cualquier otra cosa es un error inesperado. */
    public static void error(Node owner, Throwable error) {
        String message;
        if (error instanceof ServiceException se) {
            message = se.getMessage();
            if (se.getCause() != null) {
                LOG.log(Level.WARNING, message, se.getCause());
            }
        } else {
            LOG.log(Level.SEVERE, "Error inesperado", error);
            message = "Ocurrió un error inesperado: " + error.getMessage();
        }
        Alert alert = new Alert(Alert.AlertType.ERROR, message, new ButtonType("Aceptar", ButtonBar.ButtonData.OK_DONE));
        prepare(alert, owner, "Error");
        alert.showAndWait();
    }

    private static void prepare(Alert alert, Node owner, String title) {
        alert.setTitle(title);
        alert.setHeaderText(null);
        if (owner != null && owner.getScene() != null) {
            alert.initOwner(owner.getScene().getWindow());
        }
    }
}
