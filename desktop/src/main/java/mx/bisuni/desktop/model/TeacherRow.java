package mx.bisuni.desktop.model;

/** Fila de la lista de profesores. */
public record TeacherRow(
        int idTeacher,
        int idUser,
        String username,
        String firstName,
        String lastName,
        String email,
        RecordStatus status,
        String specialty,
        String phone,
        int subjectCount) {

    public String fullName() {
        return firstName + " " + lastName;
    }

    public UserData userData() {
        return new UserData(username, firstName, lastName, email, status);
    }
}
