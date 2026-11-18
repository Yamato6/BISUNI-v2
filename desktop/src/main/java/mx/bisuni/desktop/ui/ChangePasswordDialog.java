package mx.bisuni.desktop.ui;

import javafx.scene.Node;
import javafx.scene.control.PasswordField;
import mx.bisuni.desktop.service.AuthService;
import mx.bisuni.desktop.service.Passwords;
import mx.bisuni.desktop.service.ServiceException;
import mx.bisuni.desktop.session.UserSession;

import java.util.Arrays;
import java.util.Optional;

/**
 * Cambio de contraseña del usuario en sesión. Se usa de dos formas:
 * <ul>
 *   <li>Obligatorio después de entrar con una contraseña temporal: la actual ya se conoce
 *       ({@code knownCurrent}) y no se vuelve a pedir.</li>
 *   <li>Voluntario, desde la barra superior: pide la actual.</li>
 * </ul>
 */
public final class ChangePasswordDialog {

    private ChangePasswordDialog() {
    }

    /** @return la sesión actualizada si se cambió; vacío si se canceló. */
    public static Optional<UserSession> show(Node owner, AuthService auth, UserSession session, char[] knownCurrent) {
        boolean forced = knownCurrent != null;
        FormDialog form = new FormDialog(owner, forced ? "Crea tu contraseña" : "Cambiar contraseña",
                "Cambiar contraseña");
        if (forced) {
            form.addFull(FormDialog.note("Entraste con una contraseña temporal que te dio control escolar. "
                    + "Antes de continuar, crea tu propia contraseña."));
        }
        PasswordField current = forced ? null : form.add("Contraseña actual", new PasswordField());
        PasswordField next = form.add("Nueva contraseña", new PasswordField());
        PasswordField confirm = form.add("Confirmar", new PasswordField());
        form.addFull(FormDialog.note("Mínimo " + Passwords.MIN_LENGTH + " caracteres."));

        UserSession[] result = new UserSession[1];
        boolean saved = form.showAndSave(() -> {
            if (!next.getText().equals(confirm.getText())) {
                throw new ServiceException("La confirmación no coincide con la nueva contraseña.");
            }
            char[] cur = forced ? Arrays.copyOf(knownCurrent, knownCurrent.length) : current.getText().toCharArray();
            char[] nw = next.getText().toCharArray();
            return () -> {
                try {
                    result[0] = auth.changePassword(session, cur, nw);
                    return result[0];
                } finally {
                    Arrays.fill(cur, '\0');
                    Arrays.fill(nw, '\0');
                }
            };
        });
        return saved ? Optional.of(result[0]) : Optional.empty();
    }
}
