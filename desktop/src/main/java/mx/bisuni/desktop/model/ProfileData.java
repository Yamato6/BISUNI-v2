package mx.bisuni.desktop.model;

/** Datos del perfil que se crea junto con un usuario nuevo, según su rol. */
public sealed interface ProfileData {

    Role role();

    record Admin(String position) implements ProfileData {
        @Override
        public Role role() {
            return Role.Admin;
        }
    }

    record Teacher(String specialty, String phone) implements ProfileData {
        @Override
        public Role role() {
            return Role.Teacher;
        }
    }

    /** Alumno dado de alta directamente (p. ej. por traslado), sin pasar por admisión. */
    record Student(int idCareer, int semester) implements ProfileData {
        @Override
        public Role role() {
            return Role.Student;
        }
    }
}
