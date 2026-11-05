package mx.bisuni.desktop.model;

/** Elementos de catálogo que se usan en combos y filtros. */
public final class Catalog {
    private Catalog() {
    }

    public record Career(int idCareer, String name) {
        @Override
        public String toString() {
            return name;
        }
    }

    public record Group(int idGroup, String name, int idCareer, int semester) {
        @Override
        public String toString() {
            return name + " (" + semester + "° semestre)";
        }
    }

    /** Profesor activo, para combos de tutor y de asignación de materias. */
    public record Teacher(int idTeacher, String name) {
        @Override
        public String toString() {
            return name;
        }
    }

    public record Subject(int idSubject, String name, String careerName, int semester) {
        @Override
        public String toString() {
            return name + " — " + careerName + ", " + semester + "° sem.";
        }
    }
}
