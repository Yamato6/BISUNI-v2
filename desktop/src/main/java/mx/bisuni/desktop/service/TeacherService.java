package mx.bisuni.desktop.service;

import mx.bisuni.desktop.dao.TeacherDao;
import mx.bisuni.desktop.dao.UserDao;
import mx.bisuni.desktop.db.Database;
import mx.bisuni.desktop.model.RecordStatus;
import mx.bisuni.desktop.model.TeacherRow;
import mx.bisuni.desktop.model.TeacherSubject;
import mx.bisuni.desktop.model.UserData;
import mx.bisuni.desktop.session.UserSession;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/** Consulta y edición de profesores y de las materias que pueden impartir. */
public final class TeacherService {

    private final Database db;
    private final TeacherDao teachers;
    private final UserDao users;

    public TeacherService(Database db, TeacherDao teachers, UserDao users) {
        this.db = db;
        this.teachers = teachers;
        this.users = users;
    }

    public List<TeacherRow> list(RecordStatus status, String search) throws ServiceException {
        try {
            return db.withConnection(c -> teachers.list(c, status, search));
        } catch (SQLException e) {
            throw new ServiceException("No se pudo cargar la lista de profesores.", e);
        }
    }

    public void update(int idTeacher, UserData data, String specialty, String phone, UserSession admin)
            throws ServiceException {
        Guards.requireAdmin(admin);
        UserData d = Validation.user(data);
        String sp = Validation.optional(specialty, "la especialidad", 100);
        String ph = Validation.phone(phone);
        try {
            db.inTransaction(c -> {
                Optional<TeacherRow> current = teachers.findById(c, idTeacher);
                if (current.isEmpty()) {
                    throw new SQLException("El profesor ya no existe", "02000");
                }
                users.update(c, current.get().idUser(), d);
                teachers.updateProfile(c, idTeacher, sp, ph);
                return null;
            });
        } catch (SQLException e) {
            if ("02000".equals(e.getSQLState())) {
                throw new ServiceException("El profesor ya no existe. Recarga la lista.", e);
            }
            throw SqlErrors.translate(e, "No se pudo guardar el profesor.");
        }
    }

    public List<TeacherSubject> subjects(int idTeacher) throws ServiceException {
        try {
            return db.withConnection(c -> teachers.subjects(c, idTeacher));
        } catch (SQLException e) {
            throw new ServiceException("No se pudieron cargar las materias del profesor.", e);
        }
    }

    public void addSubject(int idTeacher, int idSubject, UserSession admin) throws ServiceException {
        Guards.requireAdmin(admin);
        try {
            db.withConnection(c -> {
                teachers.addSubject(c, idTeacher, idSubject);
                return null;
            });
        } catch (SQLException e) {
            throw SqlErrors.translate(e, "No se pudo asignar la materia.");
        }
    }

    /**
     * Quita la materia al profesor. Falla con un mensaje claro si la imparte en algún grupo
     * ({@code group_subject.id_teacher} referencia a {@code teacher_subject}).
     */
    public void removeSubject(int idTeacher, int idSubject, UserSession admin) throws ServiceException {
        Guards.requireAdmin(admin);
        try {
            db.withConnection(c -> teachers.removeSubject(c, idTeacher, idSubject));
        } catch (SQLException e) {
            throw SqlErrors.translate(e, "No se pudo quitar la materia.");
        }
    }
}
