package mx.bisuni.desktop.model;

/** Valores del enum {@code aspirant_status} de la base. */
public enum AspirantStatus {
    Pending("Pendiente"),
    Accepted("Aceptado"),
    Rejected("Rechazado");

    private final String label;

    AspirantStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /**
     * Transiciones permitidas en admisiones. {@code Accepted} es final: al aceptar se crean
     * {@code student_profile} y {@code enrollment}, que no se deshacen desde aquí.
     */
    public boolean canChangeTo(AspirantStatus target) {
        return this != Accepted && target != this;
    }

    @Override
    public String toString() {
        return label;
    }
}
