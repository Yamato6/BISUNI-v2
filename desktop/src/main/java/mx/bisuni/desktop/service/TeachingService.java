package mx.bisuni.desktop.service;

import mx.bisuni.desktop.dao.StudentDao;
import mx.bisuni.desktop.dao.TeachingDao;
import mx.bisuni.desktop.db.Database;
import mx.bisuni.desktop.model.StudentRow;
import mx.bisuni.desktop.model.TeacherClass;
import mx.bisuni.desktop.model.TeacherGroup;
import mx.bisuni.desktop.session.UserSession;

import java.sql.SQLException;
import java.util.List;

/** Consultas del profesor en sesión: sus clases, sus grupos y los alumnos de esos grupos. */
public final class TeachingService {

    private final Database db;
    private final TeachingDao teaching;
    private final StudentDao students;

    public TeachingService(Database db, TeachingDao teaching, StudentDao students) {
        this.db = db;
        this.teaching = teaching;
        this.students = students;
    }

    public List<TeacherClass> myClasses(UserSession teacher) throws ServiceException {
        Guards.requireTeacher(teacher);
        try {
            return db.actor(teacher.idUser()).withConnection(c -> teaching.classes(c, teacher.profileId()));
        } catch (SQLException e) {
            throw new ServiceException("No se pudieron cargar tus clases.", e);
        }
    }

    public List<TeacherGroup> myGroups(UserSession teacher) throws ServiceException {
        Guards.requireTeacher(teacher);
        try {
            return db.actor(teacher.idUser()).withConnection(c -> teaching.groups(c, teacher.profileId()));
        } catch (SQLException e) {
            throw new ServiceException("No se pudieron cargar tus grupos.", e);
        }
    }

    /** Alumnos de un grupo, solo si el profesor le da clase o es su tutor. */
    public List<StudentRow> groupStudents(int idGroup, UserSession teacher) throws ServiceException {
        Guards.requireTeacher(teacher);
        try {
            return db.actor(teacher.idUser()).withConnection(c -> {
                boolean related = teaching.groups(c, teacher.profileId()).stream()
                        .anyMatch(g -> g.idGroup() == idGroup);
                if (!related) {
                    throw new Rejected("No impartes clases en ese grupo ni eres su tutor.");
                }
                return students.list(c, new StudentDao.Filter(null, idGroup, false, null, null));
            });
        } catch (Rejected e) {
            throw new ServiceException(e.getMessage());
        } catch (SQLException e) {
            throw new ServiceException("No se pudieron cargar los alumnos del grupo.", e);
        }
    }
}
