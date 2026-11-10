package mx.bisuni.desktop.model;

/**
 * Datos editables de un grupo. {@code idTutor} es opcional. {@code idPeriod} solo se usa al crear
 * (un grupo no cambia de periodo); {@code null} = el periodo activo.
 */
public record GroupData(String name, int idCareer, int semester, Integer idTutor, RecordStatus status,
                        Integer idPeriod) {

    /** Grupo del periodo activo. */
    public GroupData(String name, int idCareer, int semester, Integer idTutor, RecordStatus status) {
        this(name, idCareer, semester, idTutor, status, null);
    }
}
