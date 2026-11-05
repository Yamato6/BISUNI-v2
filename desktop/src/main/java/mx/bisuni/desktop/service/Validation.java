package mx.bisuni.desktop.service;

import mx.bisuni.desktop.model.UserData;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.regex.Pattern;

/** Validación y normalización de datos capturados. Los mensajes van directo a la UI. */
public final class Validation {

    private static final Pattern USERNAME = Pattern.compile("[a-z0-9._-]{3,50}");
    private static final Pattern EMAIL = Pattern.compile("[^@\\s]+@[^@\\s]+\\.[^@\\s]+");
    private static final Pattern PHONE = Pattern.compile("[0-9+()\\- ]{7,20}");

    private Validation() {
    }

    /** Normaliza (recorta espacios, usuario y correo en minúsculas) y valida los datos de usuario. */
    public static UserData user(UserData d) throws ServiceException {
        if (d == null) {
            throw new ServiceException("Faltan los datos del usuario.");
        }
        String username = d.username() == null ? "" : d.username().strip().toLowerCase(Locale.ROOT);
        String email = AuthService.normalizeEmail(d.email());
        String first = required(d.firstName(), "el nombre", 50);
        String last = required(d.lastName(), "los apellidos", 50);
        if (!USERNAME.matcher(username).matches()) {
            throw new ServiceException("El usuario debe tener de 3 a 50 caracteres: letras sin acento, números, punto, guion o guion bajo.");
        }
        if (email.length() > 150 || !EMAIL.matcher(email).matches()) {
            throw new ServiceException("El correo no es válido.");
        }
        if (d.status() == null) {
            throw new ServiceException("Selecciona el estatus.");
        }
        return new UserData(username, first, last, email, d.status());
    }

    /** @param field nombre del campo con artículo, p. ej. "el nombre", "los apellidos". */
    public static String required(String value, String field, int maxLength) throws ServiceException {
        String v = value == null ? "" : value.strip();
        if (v.isEmpty()) {
            throw new ServiceException("Escribe " + field + ".");
        }
        return checkLength(v, field, maxLength);
    }

    /** Texto opcional: devuelve {@code null} si viene vacío. */
    public static String optional(String value, String field, int maxLength) throws ServiceException {
        String v = value == null ? "" : value.strip();
        return v.isEmpty() ? null : checkLength(v, field, maxLength);
    }

    private static String checkLength(String v, String field, int maxLength) throws ServiceException {
        if (v.length() > maxLength) {
            throw new ServiceException("Máximo " + maxLength + " caracteres en " + field + ".");
        }
        return v;
    }

    public static String phone(String value) throws ServiceException {
        String v = optional(value, "el teléfono", 20);
        if (v != null && !PHONE.matcher(v).matches()) {
            throw new ServiceException("El teléfono solo puede tener números, espacios, +, guiones y paréntesis (7 a 20 caracteres).");
        }
        return v;
    }

    public static int semester(int semester) throws ServiceException {
        if (semester < 1 || semester > 12) {
            throw new ServiceException("El semestre debe estar entre 1 y 12.");
        }
        return semester;
    }

    public static BigDecimal gpa(BigDecimal gpa) throws ServiceException {
        if (gpa == null) {
            throw new ServiceException("Escribe el promedio.");
        }
        if (gpa.compareTo(BigDecimal.ZERO) < 0 || gpa.compareTo(BigDecimal.TEN) > 0) {
            throw new ServiceException("El promedio debe estar entre 0 y 10.");
        }
        if (gpa.stripTrailingZeros().scale() > 2) {
            throw new ServiceException("El promedio admite máximo 2 decimales.");
        }
        return gpa.setScale(2, RoundingMode.UNNECESSARY);
    }
}
