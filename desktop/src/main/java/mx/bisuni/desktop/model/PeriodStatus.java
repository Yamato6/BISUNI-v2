package mx.bisuni.desktop.model;

/** Valores del enum {@code period_status} de la base. */
public enum PeriodStatus {
    Planned("Planeado"),
    Active("Activo"),
    Closed("Cerrado");

    private final String label;

    PeriodStatus(String label) {
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
