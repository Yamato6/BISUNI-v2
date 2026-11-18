package mx.bisuni.desktop.model;

/** Valores del enum {@code student_status} de la base: estatus académico del alumno. */
public enum StudentStatus {
    Active("Activo"),
    Leave("Baja temporal"),
    Dropped("Baja definitiva"),
    Graduated("Egresado");

    private final String label;

    StudentStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /**
     * Cambios permitidos: el egreso es definitivo; una baja definitiva puede reingresar (vuelve a
     * activo, sin grupo).
     */
    public boolean canChangeTo(StudentStatus target) {
        return switch (this) {
            case Active -> target == Leave || target == Dropped || target == Graduated;
            case Leave -> target == Active || target == Dropped;
            case Dropped -> target == Active;
            case Graduated -> false;
        };
    }

    @Override
    public String toString() {
        return label;
    }
}
