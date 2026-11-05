package mx.bisuni.desktop.model;

/** Folio de aspirante: {@code ASP-} más {@code id_aspirant} con 6 dígitos (ej. ASP-000123). */
public final class Folio {
    private Folio() {
    }

    public static String of(int idAspirant) {
        return "ASP-%06d".formatted(idAspirant);
    }
}
