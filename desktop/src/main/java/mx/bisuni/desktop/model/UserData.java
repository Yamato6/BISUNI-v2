package mx.bisuni.desktop.model;

/** Datos de {@code users} que se capturan y editan en los formularios (sin rol ni contraseña). */
public record UserData(String username, String firstName, String lastName, String email, RecordStatus status) {
}
