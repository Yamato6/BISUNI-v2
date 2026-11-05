package mx.bisuni.desktop.model;

/** Datos editables de una materia. */
public record SubjectData(String name, int idCareer, int semester, int credits, int weeklyHours,
                          RecordStatus status) {
}
