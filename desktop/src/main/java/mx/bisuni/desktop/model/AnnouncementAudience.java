package mx.bisuni.desktop.model;

/** Valores del enum {@code announcement_audience}: a quién va dirigido un aviso. */
public enum AnnouncementAudience {
    All("Toda la escuela"),
    Career("Una carrera"),
    Group("Un grupo");

    private final String label;

    AnnouncementAudience(String label) {
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
