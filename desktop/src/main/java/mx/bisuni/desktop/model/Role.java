package mx.bisuni.desktop.model;

/** Valores del enum {@code user_role} de la base. */
public enum Role {
    Admin("Administrador"),
    Teacher("Profesor"),
    Student("Alumno"),
    Aspirant("Aspirante");

    private final String label;

    Role(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** Roles que pueden usar la app de escritorio. */
    public boolean isStaff() {
        return this == Admin || this == Teacher;
    }

    /** Etiqueta en español para combos y tablas. Para SQL se usa siempre {@link #name()}. */
    @Override
    public String toString() {
        return label;
    }
}
