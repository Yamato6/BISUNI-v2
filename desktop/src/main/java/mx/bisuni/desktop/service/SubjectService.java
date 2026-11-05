package mx.bisuni.desktop.service;

import mx.bisuni.desktop.dao.SubjectDao;
import mx.bisuni.desktop.db.Database;
import mx.bisuni.desktop.model.SubjectData;
import mx.bisuni.desktop.model.SubjectRow;
import mx.bisuni.desktop.session.UserSession;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/** Catálogo de materias. */
public final class SubjectService {

    public static final int MAX_CREDITS = 30;
    public static final int MAX_WEEKLY_HOURS = 20;

    private final Database db;
    private final SubjectDao subjects;

    public SubjectService(Database db, SubjectDao subjects) {
        this.db = db;
        this.subjects = subjects;
    }

    public List<SubjectRow> list(SubjectDao.Filter filter) throws ServiceException {
        try {
            return db.withConnection(c -> subjects.list(c, filter == null ? SubjectDao.Filter.none() : filter));
        } catch (SQLException e) {
            throw new ServiceException("No se pudo cargar la lista de materias.", e);
        }
    }

    public int create(SubjectData data, UserSession admin) throws ServiceException {
        Guards.requireAdmin(admin);
        SubjectData d = validate(data);
        try {
            return db.withConnection(c -> subjects.insert(c, d));
        } catch (SQLException e) {
            throw SqlErrors.translate(e, "No se pudo crear la materia.");
        }
    }

    /**
     * Una materia que ya cursa algún grupo no puede cambiar de carrera: quedaría asignada a
     * grupos de otra carrera.
     */
    public void update(int idSubject, SubjectData data, UserSession admin) throws ServiceException {
        Guards.requireAdmin(admin);
        SubjectData d = validate(data);
        try {
            db.inTransaction(c -> {
                Optional<SubjectRow> current = subjects.findById(c, idSubject);
                if (current.isEmpty()) {
                    throw new Rejected("La materia ya no existe. Recarga la lista.");
                }
                int groups = current.get().groupCount();
                if (current.get().idCareer() != d.idCareer() && groups > 0) {
                    throw new Rejected("La materia está asignada a " + groups + (groups == 1 ? " grupo" : " grupos")
                            + "; no se puede cambiar de carrera. Quítala de esos grupos o crea una materia nueva.");
                }
                subjects.update(c, idSubject, d);
                return null;
            });
        } catch (Rejected e) {
            throw new ServiceException(e.getMessage());
        } catch (SQLException e) {
            throw SqlErrors.translate(e, "No se pudo guardar la materia.");
        }
    }

    static SubjectData validate(SubjectData d) throws ServiceException {
        if (d == null) {
            throw new ServiceException("Faltan los datos de la materia.");
        }
        String name = Validation.required(d.name(), "el nombre de la materia", 100);
        if (d.idCareer() <= 0) {
            throw new ServiceException("Selecciona la carrera.");
        }
        Validation.semester(d.semester());
        if (d.credits() < 1 || d.credits() > MAX_CREDITS) {
            throw new ServiceException("Los créditos deben estar entre 1 y " + MAX_CREDITS + ".");
        }
        if (d.weeklyHours() < 1 || d.weeklyHours() > MAX_WEEKLY_HOURS) {
            throw new ServiceException("Las horas por semana deben estar entre 1 y " + MAX_WEEKLY_HOURS + ".");
        }
        if (d.status() == null) {
            throw new ServiceException("Selecciona el estatus.");
        }
        return new SubjectData(name, d.idCareer(), d.semester(), d.credits(), d.weeklyHours(), d.status());
    }
}
