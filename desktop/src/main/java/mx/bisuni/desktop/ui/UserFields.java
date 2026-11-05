package mx.bisuni.desktop.ui;

import javafx.scene.control.ComboBox;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import mx.bisuni.desktop.model.RecordStatus;
import mx.bisuni.desktop.model.UserData;
import mx.bisuni.desktop.service.ServiceException;

import java.util.Arrays;

/** Campos de datos de usuario compartidos por los formularios de usuarios, alumnos y profesores. */
public final class UserFields {

    private final TextField username = new TextField();
    private final TextField firstName = new TextField();
    private final TextField lastName = new TextField();
    private final TextField email = new TextField();
    private final ComboBox<RecordStatus> status = new ComboBox<>();
    private PasswordField password;
    private PasswordField confirm;

    /** Agrega los campos al formulario. */
    public UserFields(FormDialog form) {
        form.add("Nombre(s)", firstName);
        form.add("Apellidos", lastName);
        form.add("Usuario", username);
        form.add("Correo", email);
        form.add("Estatus", status);
        username.setPromptText("p. ej. jperez");
        email.setPromptText("nombre@bisuni.edu");
        status.getItems().setAll(RecordStatus.values());
        status.setValue(RecordStatus.Active);
    }

    /**
     * Agrega contraseña y confirmación.
     *
     * @param required {@code false} en edición: vacío significa "no cambiar".
     */
    public void addPassword(FormDialog form, boolean required) {
        password = form.add(required ? "Contraseña" : "Nueva contraseña", new PasswordField());
        confirm = form.add("Confirmar", new PasswordField());
        password.setPromptText(required ? "Mínimo 8 caracteres" : "Déjala vacía para no cambiarla");
    }

    public void fill(UserData d) {
        username.setText(d.username());
        firstName.setText(d.firstName());
        lastName.setText(d.lastName());
        email.setText(d.email());
        status.setValue(d.status());
    }

    /** Datos tal como se capturaron; la normalización y validación las hace el servicio. */
    public UserData read() {
        return new UserData(username.getText(), firstName.getText(), lastName.getText(), email.getText(),
                status.getValue());
    }

    /** Contraseña capturada (vacía si no se escribió). Valida que coincida con la confirmación. */
    public char[] readPassword() throws ServiceException {
        if (password == null) {
            return new char[0];
        }
        char[] p = password.getText().toCharArray();
        char[] c = confirm.getText().toCharArray();
        boolean same = Arrays.equals(p, c);
        Arrays.fill(c, '\0');
        if (!same) {
            Arrays.fill(p, '\0');
            throw new ServiceException("La contraseña y su confirmación no coinciden.");
        }
        return p;
    }

    /** No permite cambiar el estatus (p. ej. el admin editándose a sí mismo). */
    public void lockStatus(String reason) {
        status.setDisable(true);
        status.setTooltip(new javafx.scene.control.Tooltip(reason));
    }

    public TextField firstNameField() {
        return firstName;
    }
}
