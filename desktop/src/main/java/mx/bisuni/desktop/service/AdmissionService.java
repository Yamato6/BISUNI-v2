package mx.bisuni.desktop.service;

import mx.bisuni.desktop.dao.AdmissionDao;
import mx.bisuni.desktop.dao.AspirantDao;
import mx.bisuni.desktop.db.Database;
import mx.bisuni.desktop.model.AspirantRow;
import mx.bisuni.desktop.model.AspirantStatus;
import mx.bisuni.desktop.model.Folio;
import mx.bisuni.desktop.session.UserSession;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Admisión de aspirantes. Corrige dos fallas de la v1: solo procesa las filas que cambiaron
 * (antes reprocesaba todas y duplicaba {@code student_profile}) y llena {@code enrollment}.
 */
public final class AdmissionService {

    /** Cambio de estatus capturado en la UI: lo que se leyó de la base y lo que se quiere. */
    public record Change(AspirantStatus from, AspirantStatus to) {
    }

    /** Resumen de lo aplicado. */
    public record Result(int accepted, int rejected, int pending) {
        public int total() {
            return accepted + rejected + pending;
        }
    }

    private final Database db;
    private final AspirantDao aspirants;
    private final AdmissionDao admissions;

    public AdmissionService(Database db, AspirantDao aspirants, AdmissionDao admissions) {
        this.db = db;
        this.aspirants = aspirants;
        this.admissions = admissions;
    }

    public List<AspirantRow> list(AspirantStatus filter) throws ServiceException {
        try {
            return db.withConnection(c -> aspirants.list(c, filter));
        } catch (SQLException e) {
            throw new ServiceException("No se pudo cargar la lista de aspirantes.", e);
        }
    }

    /**
     * Aplica los cambios en una sola transacción: o se aplican todos o ninguno.
     *
     * @param changes id_aspirant → cambio. Solo las filas modificadas.
     */
    public Result applyChanges(Map<Integer, Change> changes, UserSession admin) throws ServiceException {
        if (admin == null || !admin.isAdmin()) {
            throw new ServiceException("Solo un administrador puede procesar admisiones.");
        }
        if (changes == null || changes.isEmpty()) {
            return new Result(0, 0, 0);
        }
        // Validación previa, sin tocar la base.
        for (var e : changes.entrySet()) {
            Change ch = e.getValue();
            if (!ch.from().canChangeTo(ch.to())) {
                throw new ServiceException(ch.from() == AspirantStatus.Accepted
                        ? "El aspirante " + Folio.of(e.getKey()) + " ya fue aceptado; su admisión no se puede revertir."
                        : "Cambio no válido para " + Folio.of(e.getKey()) + ".");
            }
        }
        // Orden estable por id para que dos admins concurrentes bloqueen filas en el mismo orden.
        Map<Integer, Change> ordered = new TreeMap<>(changes);
        try {
            return db.actor(admin.idUser()).inTransaction(conn -> {
                int accepted = 0, rejected = 0, pending = 0;
                for (var e : ordered.entrySet()) {
                    int idAspirant = e.getKey();
                    Change ch = e.getValue();
                    Optional<AspirantDao.Transition> t =
                            aspirants.updateStatusIfCurrent(conn, idAspirant, ch.from(), ch.to());
                    if (t.isEmpty()) {
                        throw new ConcurrentChange(idAspirant);
                    }
                    switch (ch.to()) {
                        case Accepted -> {
                            admissions.promoteToStudent(conn, t.get().idUser());
                            int idStudent = admissions.createStudentProfile(conn, idAspirant, t.get().idUser(), t.get().idCareer());
                            admissions.createEnrollment(conn, idAspirant, idStudent, admin.profileId());
                            accepted++;
                        }
                        case Rejected -> rejected++;
                        case Pending -> pending++;
                    }
                }
                return new Result(accepted, rejected, pending);
            });
        } catch (ConcurrentChange e) {
            throw new ServiceException("El aspirante " + Folio.of(e.idAspirant)
                    + " fue modificado por otra persona mientras editabas. Recarga la lista; no se guardó ningún cambio.");
        } catch (SQLException e) {
            throw new ServiceException("No se pudieron guardar los cambios; no se aplicó ninguno. Detalle: "
                    + e.getMessage(), e);
        }
    }

    /** Señal interna para abortar la transacción cuando el estatus ya no es el esperado. */
    private static final class ConcurrentChange extends RuntimeException {
        final int idAspirant;

        ConcurrentChange(int idAspirant) {
            super(null, null, false, false);
            this.idAspirant = idAspirant;
        }
    }
}
