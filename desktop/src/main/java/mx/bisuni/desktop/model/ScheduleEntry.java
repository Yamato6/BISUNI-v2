package mx.bisuni.desktop.model;

/** Una clase del horario: día, bloque, grupo, materia y profesor. */
public record ScheduleEntry(
        Weekday day,
        int idSlot,
        int idGroup,
        String groupName,
        int idSubject,
        String subjectName,
        int idTeacher,
        String teacherName) {
}
