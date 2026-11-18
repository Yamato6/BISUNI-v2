package mx.bisuni.desktop.ui.users;

import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import mx.bisuni.desktop.model.UserRow;

/** Muestra la contraseña temporal recién generada, con un botón para copiarla. */
final class TemporaryPasswordDialog {

    private TemporaryPasswordDialog() {
    }

    static void show(Node owner, UserRow user, String temp) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Contraseña temporal");
        alert.setHeaderText("Contraseña temporal de " + user.firstName() + " " + user.lastName());
        if (owner != null && owner.getScene() != null) {
            alert.initOwner(owner.getScene().getWindow());
        }
        TextField field = new TextField(temp);
        field.setEditable(false);
        field.setPrefColumnCount(16);
        field.getStyleClass().add("temp-password");
        Button copy = new Button("Copiar");
        copy.setOnAction(e -> {
            ClipboardContent c = new ClipboardContent();
            c.putString(temp);
            Clipboard.getSystemClipboard().setContent(c);
            copy.setText("Copiada");
        });
        Label note = new Label("Entrégasela al usuario (" + user.email() + "). Solo se muestra esta vez: "
                + "al entrar con ella, en el escritorio o en el portal, deberá crear su propia contraseña.");
        note.setWrapText(true);
        note.setMaxWidth(420);
        VBox box = new VBox(10, new HBox(8, field, copy), note);
        alert.getDialogPane().setContent(box);
        alert.showAndWait();
    }
}
