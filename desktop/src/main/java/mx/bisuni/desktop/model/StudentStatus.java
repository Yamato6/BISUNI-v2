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

    @Override
    public String toString() {
        return label;
    }
}
