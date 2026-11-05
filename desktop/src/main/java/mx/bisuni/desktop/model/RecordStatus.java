package mx.bisuni.desktop.model;

/** Valores del enum {@code record_status} de la base. */
public enum RecordStatus {
    Active("Activo"),
    Inactive("Inactivo");

    private final String label;

    RecordStatus(String label) {
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
