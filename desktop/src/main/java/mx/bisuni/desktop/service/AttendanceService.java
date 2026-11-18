package mx.bisuni.desktop.service;

import mx.bisuni.desktop.dao.AttendanceDao;
import mx.bisuni.desktop.dao.TeachingDao;
import mx.bisuni.desktop.db.Database;
import mx.bisuni.desktop.model.AttendanceRow;
import mx.bisuni.desktop.model.AttendanceStatus;
import mx.bisuni.desktop.model.Weekday;
import mx.bisuni.desktop.session.UserSession;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/**
 * Pase de lista del profesor. La base limita la asistencia al periodo activo y a sus fechas
 * (trigger {@code attendance_guard}); aquí además se impide registrar fechas futuras.
 */
public final class AttendanceService {

    /** Porcentaje de faltas a partir del cual un alumno está en riesgo. */
    public static final BigDecimal RISK_PCT = new BigDecimal("20");

    private final Database db;
    private final AttendanceDao attendance;
    private final TeachingDao teaching;

    public AttendanceService(Database db, AttendanceDao attendance, TeachingDao teaching) {
        this.db = db;
        this.attendance = attendance;
        this.teaching = teaching;
    }

    public static boolean atRisk(BigDecimal absencePct) {
        return absencePct != null && absencePct.compareTo(RISK_PCT) > 0;
    }

    /** Lista de la clase en {@code date}, con la marca de ese día y las faltas acumuladas de cada alumno. */
    public List<AttendanceRow> sheet(int idGroup, int idSubject, LocalDate date, UserSession teacher)
            throws ServiceException {
        Guards.requireTeacher(teacher);
        try {
            return db.withConnection(c -> {
                requireTeaches(c, teacher, idGroup, idSubject);
                return attendance.sheet(c, idGroup, idSubject, date);
            });
        } catch (Rejected e) {
            throw new ServiceException(e.getMessage());
        } catch (SQLException e) {
            throw new ServiceException("No se pudo cargar la lista de asistencia.", e);
        }
    }

    /** Días en que la clase está en el horario (para advertir si se pasa lista otro día). */
    public Set<Weekday> scheduledDays(int idGroup, int idSubject) throws ServiceException {
        try {
            return db.withConnection(c -> attendance.scheduledDays(c, idGroup, idSubject));
        } catch (SQLException e) {
            throw new ServiceException("No se pudo consultar el horario de la clase.", e);
        }
    }

    public List<LocalDate> recordedDates(int idGroup, int idSubject) throws ServiceException {
        try {
            return db.withConnection(c -> attendance.recordedDates(c, idGroup, idSubject));
        } catch (SQLException e) {
            throw new ServiceException("No se pudieron consultar las fechas registradas.", e);
        }
    }

    /** ¿{@code date} es un día con clase según el horario? Sábado y domingo nunca. */
    public static boolean isClassDay(LocalDate date, Set<Weekday> days) {
        DayOfWeek dow = date.getDayOfWeek();
        if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) {
            return false;
        }
        return days.contains(Weekday.values()[dow.getValue() - 1]);
    }

    /**
     * Guarda las marcas del día (UPSERT por alumno). Solo alumnos que están en el grupo.
     *
     * @return marcas guardadas
     */
    public int save(int idGroup, int idSubject, LocalDate date, Map<Integer, AttendanceStatus> marks,
                    UserSession teacher) throws ServiceException {
        Guards.requireTeacher(teacher);
        if (date == null) {
            throw new ServiceException("Elige la fecha de la clase.");
        }
        if (date.isAfter(LocalDate.now())) {
            throw new ServiceException("No se puede pasar lista de una fecha futura.");
        }
        if (marks == null || marks.isEmpty()) {
            return 0;
        }
        Map<Integer, AttendanceStatus> ordered = new TreeMap<>(marks);
        try {
            return db.actor(teacher.idUser()).inTransaction(c -> {
                requireTeaches(c, teacher, idGroup, idSubject);
                Set<Integer> enrolled = new java.util.HashSet<>();
                attendance.sheet(c, idGroup, idSubject, date).forEach(r -> enrolled.add(r.idStudent()));
                int saved = 0;
                for (var e : ordered.entrySet()) {
                    if (e.getValue() == null) {
                        continue;
                    }
                    if (!enrolled.contains(e.getKey())) {
                        throw new Rejected("Uno de los alumnos ya no está en el grupo. Recarga la lista.");
                    }
                    attendance.upsert(c, idGroup, idSubject, date, e.getKey(), e.getValue(), teacher.idUser());
                    saved++;
                }
                return saved;
            });
        } catch (Rejected e) {
            throw new ServiceException(e.getMessage());
        } catch (SQLException e) {
            throw SqlErrors.translate(e, "No se pudo guardar la asistencia; no se guardó ninguna marca.");
        }
    }

    /** Fecha sugerida al abrir la vista: hoy, o el último día con clase si hoy no hay. */
    public static Optional<LocalDate> lastClassDay(LocalDate from, Set<Weekday> days) {
        if (days.isEmpty()) {
            return Optional.empty();
        }
        LocalDate d = from;
        for (int i = 0; i < 7; i++, d = d.minusDays(1)) {
            if (isClassDay(d, days)) {
                return Optional.of(d);
            }
        }
        return Optional.empty();
    }

    private void requireTeaches(Connection c, UserSession teacher, int idGroup, int idSubject) throws SQLException {
        if (!teaching.teaches(c, teacher.profileId(), idGroup, idSubject)) {
            throw new Rejected("No impartes esa materia en ese grupo.");
        }
    }
}
