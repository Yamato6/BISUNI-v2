package mx.bisuni.desktop.service;

import mx.bisuni.desktop.dao.PeriodDao;
import mx.bisuni.desktop.db.Database;
import mx.bisuni.desktop.model.Period;
import mx.bisuni.desktop.model.PeriodData;
import mx.bisuni.desktop.model.PeriodStatus;
import mx.bisuni.desktop.session.UserSession;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Periodos escolares: Planeado → Activo → Cerrado.
 * <ul>
 *   <li>Solo hay un periodo activo (índice único en la base).</li>
 *   <li>Cerrar un periodo exige que todas sus actas estén cerradas; un periodo cerrado ya no se edita
 *       y sus calificaciones quedan como historial (lo garantiza también un trigger).</li>
 *   <li>Activar un periodo planeado cierra el que estaba activo, con la misma condición.</li>
 *   <li>La convocatoria ({@code admissions_open}) habilita el registro de aspirantes en la web.</li>
 * </ul>
 */
public final class PeriodService {

    private static final int MAX_LISTED = 6;

    private final Database db;
    private final PeriodDao periods;

    public PeriodService(Database db, PeriodDao periods) {
        this.db = db;
        this.periods = periods;
    }

    public List<Period> list() throws ServiceException {
        try {
            return db.withConnection(periods::list);
        } catch (SQLException e) {
            throw new ServiceException("No se pudo cargar la lista de periodos.", e);
        }
    }

    public Optional<Period> active() throws ServiceException {
        try {
            return db.withConnection(periods::active);
        } catch (SQLException e) {
            throw new ServiceException("No se pudo consultar el periodo activo.", e);
        }
    }

    public int create(PeriodData data, UserSession admin) throws ServiceException {
        Guards.requireAdmin(admin);
        PeriodData d = validate(data);
        try {
            return db.actor(admin.idUser()).inTransaction(c -> {
                checkOverlap(c, d, null);
                return periods.insert(c, d);
            });
        } catch (Rejected e) {
            throw new ServiceException(e.getMessage());
        } catch (SQLException e) {
            throw translate(e, "No se pudo crear el periodo.");
        }
    }

    public void update(int idPeriod, PeriodData data, UserSession admin) throws ServiceException {
        Guards.requireAdmin(admin);
        PeriodData d = validate(data);
        try {
            db.actor(admin.idUser()).inTransaction(c -> {
                Period p = require(c, idPeriod);
                if (p.isClosed()) {
                    throw new Rejected("El periodo " + p.name() + " ya está cerrado; no se puede modificar.");
                }
                checkOverlap(c, d, idPeriod);
                periods.update(c, idPeriod, d);
                return null;
            });
        } catch (Rejected e) {
            throw new ServiceException(e.getMessage());
        } catch (SQLException e) {
            throw translate(e, "No se pudo guardar el periodo.");
        }
    }

    /** Abre o cierra la convocatoria de aspirantes del periodo. */
    public void setAdmissionsOpen(int idPeriod, boolean open, UserSession admin) throws ServiceException {
        Guards.requireAdmin(admin);
        try {
            db.actor(admin.idUser()).inTransaction(c -> {
                Period p = require(c, idPeriod);
                if (open && p.isClosed()) {
                    throw new Rejected("El periodo " + p.name() + " ya está cerrado; no puede recibir aspirantes.");
                }
                periods.setAdmissionsOpen(c, idPeriod, open);
                return null;
            });
        } catch (Rejected e) {
            throw new ServiceException(e.getMessage());
        } catch (SQLException e) {
            throw translate(e, "No se pudo cambiar la convocatoria.");
        }
    }

    /** Activa un periodo planeado; el que estaba activo se cierra (si sus actas lo permiten). */
    public void activate(int idPeriod, UserSession admin) throws ServiceException {
        Guards.requireAdmin(admin);
        try {
            db.actor(admin.idUser()).inTransaction(c -> {
                Period p = require(c, idPeriod);
                if (p.status() != PeriodStatus.Planned) {
                    throw new Rejected("Solo se puede activar un periodo planeado; " + p.name() + " está "
                            + p.status().label().toLowerCase() + ".");
                }
                Optional<Period> current = periods.active(c);
                if (current.isPresent()) {
                    requireActasClosed(c, current.get(), "Para activar " + p.name() + " se cierra " + current.get().name());
                    periods.setStatus(c, current.get().idPeriod(), PeriodStatus.Closed);
                }
                periods.setStatus(c, idPeriod, PeriodStatus.Active);
                return null;
            });
        } catch (Rejected e) {
            throw new ServiceException(e.getMessage());
        } catch (SQLException e) {
            throw translate(e, "No se pudo activar el periodo.");
        }
    }

    /** Cierra el periodo activo. Exige que todas sus actas estén cerradas. */
    public void close(int idPeriod, UserSession admin) throws ServiceException {
        Guards.requireAdmin(admin);
        try {
            db.actor(admin.idUser()).inTransaction(c -> {
                Period p = require(c, idPeriod);
                if (p.status() != PeriodStatus.Active) {
                    throw new Rejected("Solo se cierra el periodo activo; " + p.name() + " está "
                            + p.status().label().toLowerCase() + ".");
                }
                requireActasClosed(c, p, "No se puede cerrar " + p.name());
                periods.setStatus(c, idPeriod, PeriodStatus.Closed);
                return null;
            });
        } catch (Rejected e) {
            throw new ServiceException(e.getMessage());
        } catch (SQLException e) {
            throw translate(e, "No se pudo cerrar el periodo.");
        }
    }

    private void requireActasClosed(java.sql.Connection c, Period p, String prefix) throws SQLException {
        List<String> open = periods.openActas(c, p.idPeriod());
        if (!open.isEmpty()) {
            String listed = String.join("\n  • ", open.subList(0, Math.min(MAX_LISTED, open.size())));
            String more = open.size() > MAX_LISTED ? "\n  … y " + (open.size() - MAX_LISTED) + " más" : "";
            throw new Rejected(prefix + ": tiene " + open.size() + (open.size() == 1 ? " acta abierta" : " actas abiertas")
                    + ". Los profesores deben cerrarlas (o quita las materias sin alumnos):\n  • " + listed + more);
        }
    }

    private void checkOverlap(java.sql.Connection c, PeriodData d, Integer exclude) throws SQLException {
        Optional<String> other = periods.overlapping(c, d, exclude);
        if (other.isPresent()) {
            throw new Rejected("Las fechas se traslapan con el periodo " + other.get() + ".");
        }
    }

    private Period require(java.sql.Connection c, int idPeriod) throws SQLException {
        return periods.findById(c, idPeriod).orElseThrow(() -> new Rejected("El periodo ya no existe. Recarga la lista."));
    }

    static PeriodData validate(PeriodData d) throws ServiceException {
        if (d == null) {
            throw new ServiceException("Faltan los datos del periodo.");
        }
        String name = Validation.required(d.name(), "el nombre del periodo (p. ej. 2027-1)", 20);
        if (d.startDate() == null || d.endDate() == null) {
            throw new ServiceException("Indica las fechas de inicio y fin.");
        }
        if (!d.endDate().isAfter(d.startDate())) {
            throw new ServiceException("La fecha de fin debe ser posterior a la de inicio.");
        }
        return new PeriodData(name, d.startDate(), d.endDate());
    }

    private static ServiceException translate(SQLException e, String fallback) {
        return SqlErrors.translate(e, fallback, Map.of());
    }
}
