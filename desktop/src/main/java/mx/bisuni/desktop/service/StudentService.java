package mx.bisuni.desktop.service;

import mx.bisuni.desktop.dao.StudentDao;
import mx.bisuni.desktop.dao.UserDao;
import mx.bisuni.desktop.db.Database;
import mx.bisuni.desktop.model.StudentRow;
import mx.bisuni.desktop.model.StudentUpdate;
import mx.bisuni.desktop.model.UserData;
import mx.bisuni.desktop.session.UserSession;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/**
 * Consulta y edición de alumnos. A diferencia de la v1, cambiar de grupo NO borra las
 * calificaciones: cada calificación está ligada al grupo donde se cursó la materia.
 */
public final class StudentService {

    private final Database db;
    private final StudentDao students;
    private final UserDao users;

    public StudentService(Database db, StudentDao students, UserDao users) {
        this.db = db;
        this.students = students;
        this.users = users;
    }

    public List<StudentRow> list(StudentDao.Filter filter) throws ServiceException {
        try {
            return db.withConnection(c -> students.list(c, filter == null ? StudentDao.Filter.none() : filter));
        } catch (SQLException e) {
            throw new ServiceException("No se pudo cargar la lista de alumnos.", e);
        }
    }

    public void update(int idStudent, StudentUpdate u, UserSession admin) throws ServiceException {
        Guards.requireAdmin(admin);
        if (u == null) {
            throw new ServiceException("Faltan los datos del alumno.");
        }
        UserData d = Validation.user(u.user());
        int semester = Validation.semester(u.semester());
        if (u.idCareer() <= 0) {
            throw new ServiceException("Selecciona la carrera del alumno.");
        }
        try {
            db.actor(admin.idUser()).inTransaction(c -> {
                Optional<StudentRow> current = students.findById(c, idStudent);
                if (current.isEmpty()) {
                    throw new SQLException("El alumno ya no existe", "02000");
                }
                users.update(c, current.get().idUser(), d);
                students.updateProfile(c, idStudent, u.idCareer(), semester, u.idGroup());
                return null;
            });
        } catch (SQLException e) {
            if ("02000".equals(e.getSQLState())) {
                throw new ServiceException("El alumno ya no existe. Recarga la lista.", e);
            }
            throw SqlErrors.translate(e, "No se pudo guardar el alumno.");
        }
    }
}
