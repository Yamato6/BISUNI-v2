package mx.bisuni.desktop.service;

import mx.bisuni.desktop.dao.CareerDao;
import mx.bisuni.desktop.db.Database;
import mx.bisuni.desktop.model.CareerData;
import mx.bisuni.desktop.model.CareerRow;
import mx.bisuni.desktop.model.RecordStatus;
import mx.bisuni.desktop.session.UserSession;

import java.sql.SQLException;
import java.util.List;
import java.util.Locale;

/**
 * Catálogo de carreras (en la v1 solo existían en el script de datos).
 * <ul>
 *   <li>La clave forma la matrícula ({@code 2026IT0001}): no se cambia si la carrera ya tiene alumnos.</li>
 *   <li>El número de semestres no puede quedar por debajo del de sus materias; marca cuándo un grupo
 *       egresa en lugar de promoverse.</li>
 *   <li>Una carrera inactiva deja de aparecer en el registro de aspirantes y en los catálogos.</li>
 * </ul>
 */
public final class CareerService {

    private final Database db;
    private final CareerDao careers;

    public CareerService(Database db, CareerDao careers) {
        this.db = db;
        this.careers = careers;
    }

    public List<CareerRow> list(RecordStatus status) throws ServiceException {
        try {
            return db.withConnection(c -> careers.list(c, status));
        } catch (SQLException e) {
            throw new ServiceException("No se pudo cargar la lista de carreras.", e);
        }
    }

    public int create(CareerData data, UserSession admin) throws ServiceException {
        Guards.requireAdmin(admin);
        CareerData d = validate(data);
        try {
            return db.actor(admin.idUser()).inTransaction(c -> careers.insert(c, d));
        } catch (SQLException e) {
            throw SqlErrors.translate(e, "No se pudo crear la carrera.");
        }
    }

    public void update(int idCareer, CareerData data, UserSession admin) throws ServiceException {
        Guards.requireAdmin(admin);
        CareerData d = validate(data);
        try {
            db.actor(admin.idUser()).inTransaction(c -> {
                CareerRow current = careers.findById(c, idCareer)
                        .orElseThrow(() -> new Rejected("La carrera ya no existe. Recarga la lista."));
                if (!current.code().equals(d.code()) && current.studentCount() > 0) {
                    throw new Rejected("La clave forma parte de la matrícula de sus " + current.studentCount()
                            + " alumnos; no se puede cambiar.");
                }
                if (d.totalSemesters() < current.maxSemester()) {
                    throw new Rejected("La carrera tiene materias de " + current.maxSemester()
                            + "° semestre; no puede durar menos semestres.");
                }
                careers.update(c, idCareer, d);
                return null;
            });
        } catch (Rejected e) {
            throw new ServiceException(e.getMessage());
        } catch (SQLException e) {
            throw SqlErrors.translate(e, "No se pudo guardar la carrera.");
        }
    }

    static CareerData validate(CareerData d) throws ServiceException {
        if (d == null) {
            throw new ServiceException("Faltan los datos de la carrera.");
        }
        String name = Validation.required(d.name(), "el nombre de la carrera", 100);
        String code = Validation.required(d.code(), "la clave", 4).toUpperCase(Locale.ROOT);
        if (!code.matches("[A-Z]{2,4}")) {
            throw new ServiceException("La clave son de 2 a 4 letras sin acentos (p. ej. IT).");
        }
        Validation.semester(d.totalSemesters());
        if (d.status() == null) {
            throw new ServiceException("Selecciona el estatus.");
        }
        return new CareerData(name, code, d.totalSemesters(), d.status());
    }
}
