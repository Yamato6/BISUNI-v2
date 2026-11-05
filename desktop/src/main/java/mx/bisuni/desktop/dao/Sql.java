package mx.bisuni.desktop.dao;

/** Utilidades para armar parámetros de consultas. */
final class Sql {
    private Sql() {
    }

    /**
     * Patrón para {@code ILIKE ? ESCAPE '\'} que busca {@code text} en cualquier posición, tratando
     * {@code %} y {@code _} como texto literal. Devuelve {@code null} si no hay texto que buscar.
     */
    static String containsPattern(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String escaped = text.strip()
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + escaped + "%";
    }

    static String name(Enum<?> e) {
        return e == null ? null : e.name();
    }
}
