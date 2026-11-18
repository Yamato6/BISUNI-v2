package mx.bisuni.desktop.service;

import at.favre.lib.crypto.bcrypt.BCrypt;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

/** Hash y verificación de contraseñas con bcrypt (compatible con bcryptjs de la web). */
public final class Passwords {

    public static final int MIN_LENGTH = 8;
    /** bcrypt solo usa los primeros 72 bytes. */
    public static final int MAX_LENGTH = 72;
    private static final int COST = 10;
    /** Sin caracteres que se confunden al dictarlos o copiarlos (0/O, 1/l/I). */
    private static final String TEMP_ALPHABET = "abcdefghijkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int TEMP_LENGTH = 10;
    private static final SecureRandom RANDOM = new SecureRandom();

    private Passwords() {
    }

    public static String hash(char[] password) {
        return BCrypt.withDefaults().hashToString(COST, password);
    }

    public static boolean verify(char[] password, String hash) {
        return BCrypt.verifyer().verify(password, hash.getBytes(StandardCharsets.UTF_8)).verified;
    }

    /** Contraseña temporal aleatoria para un restablecimiento. */
    public static char[] temporary() {
        char[] out = new char[TEMP_LENGTH];
        for (int i = 0; i < out.length; i++) {
            out[i] = TEMP_ALPHABET.charAt(RANDOM.nextInt(TEMP_ALPHABET.length()));
        }
        return out;
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
