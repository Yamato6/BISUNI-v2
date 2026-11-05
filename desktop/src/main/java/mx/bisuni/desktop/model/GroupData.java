package mx.bisuni.desktop.model;

/** Datos editables de un grupo. {@code idTutor} es opcional. */
public record GroupData(String name, int idCareer, int semester, Integer idTutor, RecordStatus status) {
}
