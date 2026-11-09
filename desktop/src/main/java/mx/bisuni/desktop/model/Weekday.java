package mx.bisuni.desktop.model;

/** Valores del enum {@code weekday} de la base (lunes a viernes). */
public enum Weekday {
    Mon("Lunes"),
    Tue("Martes"),
    Wed("Miércoles"),
    Thu("Jueves"),
    Fri("Viernes");

    private final String label;

    Weekday(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    @Override
    public String toString() {
        return label;
    }
}
