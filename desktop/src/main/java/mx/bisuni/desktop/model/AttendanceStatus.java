package mx.bisuni.desktop.model;

/** Valores del enum {@code attendance_status}. Solo {@link #Absent} cuenta como falta. */
public enum AttendanceStatus {
    Present("Presente"),
    Absent("Falta"),
    Late("Retardo"),
    Excused("Justificada");

    private final String label;

    AttendanceStatus(String label) {
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
