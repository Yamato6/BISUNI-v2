package mx.bisuni.desktop.service;

import at.favre.lib.crypto.bcrypt.BCrypt;

import java.nio.charset.StandardCharsets;

/** Hash y verificación de contraseñas con bcrypt (compatible con bcryptjs de la web). */
public final class Passwords {

    public static final int MIN_LENGTH = 8;
    /** bcrypt solo usa los primeros 72 bytes. */
    public static final int MAX_LENGTH = 72;
    private static final int COST = 10;

    private Passwords() {
    }

    public static String hash(char[] password) {
        return BCrypt.withDefaults().hashToString(COST, password);
    }

    public static boolean verify(char[] password, String hash) {
        return BCrypt.verifyer().verify(password, hash.getBytes(StandardCharsets.UTF_8)).verified;
    }

    /** Valida longitud; lanza {@link ServiceException} con mensaje para la UI. */
    public static void validate(char[] password) throws ServiceException {
        if (password == null || password.length < MIN_LENGTH) {
            throw new ServiceException("La contraseña debe tener al menos " + MIN_LENGTH + " caracteres.");
        }
        if (new String(password).getBytes(StandardCharsets.UTF_8).length > MAX_LENGTH) {
            throw new ServiceException("La contraseña es demasiado larga (máximo " + MAX_LENGTH + " bytes).");
        }
    }
}
