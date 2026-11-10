package mx.bisuni.desktop.service;

import mx.bisuni.desktop.dao.GroupDao;
import mx.bisuni.desktop.dao.ScheduleDao;
import mx.bisuni.desktop.db.Database;
import mx.bisuni.desktop.model.GroupSubjectRow;
import mx.bisuni.desktop.model.ScheduleEntry;
import mx.bisuni.desktop.model.TimeSlot;
import mx.bisuni.desktop.model.Weekday;
import mx.bisuni.desktop.session.UserSession;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/**
 * Horarios. Solo se edita el horario de grupo; el del profesor se deriva de él
 * ({@code v_teacher_schedule}), así que nunca se desincronizan (en la v1 eran dos tablas).
 * <p>
 * Los choques los impide la base ({@code UNIQUE} por grupo y por profesor dentro del periodo); aquí
 * se revisan antes para dar un mensaje que diga con quién choca. Una materia no puede tener más
 * bloques que sus horas semanales ({@code weekly_hours}), y el horario de un grupo de un periodo
 * cerrado ya no se edita.
 */
public final class ScheduleService {

    private final Database db;
    private final ScheduleDao schedule;
    private final GroupDao groups;

    public ScheduleService(Database db, ScheduleDao schedule, GroupDao groups) {
        this.db = db;
        this.schedule = schedule;
        this.groups = groups;
    }

    public List<TimeSlot> timeSlots() throws ServiceException {
        try {
            return db.withConnection(schedule::timeSlots);
        } catch (SQLException e) {
            throw new ServiceException("No se pudo cargar el catálogo de bloques horarios.", e);
        }
    }

    public List<ScheduleEntry> forGroup(int idGroup) throws ServiceException {
        try {
            return db.withConnection(c -> schedule.forGroup(c, idGroup));
        } catch (SQLException e) {
            throw new ServiceException("No se pudo cargar el horario del grupo.", e);
        }
    }

    public List<ScheduleEntry> forTeacher(int idTeacher) throws ServiceException {
        try {
            return db.withConnection(c -> schedule.forTeacher(c, idTeacher));
        } catch (SQLException e) {
            throw new ServiceException("No se pudo cargar el horario del profesor.", e);
        }
    }

    /**
     * Pone {@code idSubject} en el día y bloque del grupo, reemplazando lo que hubiera. El profesor
     * es el asignado a la materia en el grupo.
     */
    public void assign(int idGroup, Weekday day, TimeSlot slot, int idSubject, UserSession admin)
            throws ServiceException {
        Guards.requireAdmin(admin);
        try {
            db.actor(admin.idUser()).inTransaction(c -> {
                requireEditable(c, idGroup);
                GroupSubjectRow gs = groups.subjects(c, idGroup).stream()
                        .filter(s -> s.idSubject() == idSubject).findFirst()
                        .orElseThrow(() -> new Rejected("La materia ya no está asignada al grupo. Recarga."));
                if (!gs.hasTeacher()) {
                    throw new Rejected("«" + gs.subjectName() + "» no tiene profesor asignado en este grupo. "
                            + "Asígnalo en Grupos → Materias y alumnos.");
                }
                Optional<ScheduleEntry> current = schedule.at(c, idGroup, day, slot.idSlot());
                if (current.isPresent() && current.get().idSubject() == idSubject) {
                    return null; // ya estaba
                }
                if (gs.scheduledHours() >= gs.weeklyHours()) {
                    throw new Rejected("«" + gs.subjectName() + "» ya tiene sus " + gs.weeklyHours()
                            + " horas semanales en el horario. Quita una de sus clases para moverla.");
                }
                Optional<ScheduleEntry> busy = schedule.teacherBusy(c, gs.idTeacher(), day, slot.idSlot(), idGroup);
                if (busy.isPresent()) {
                    throw new Rejected(gs.teacherName() + " ya tiene clase el " + day.label().toLowerCase()
                            + " " + slot.spoken() + " en " + busy.get().groupName()
                            + " (" + busy.get().subjectName() + ").");
                }
                schedule.delete(c, idGroup, day, slot.idSlot());
                schedule.insert(c, idGroup, day, slot.idSlot(), idSubject);
                return null;
            });
        } catch (Rejected e) {
            throw new ServiceException(e.getMessage());
        } catch (SQLException e) {
            // Respaldo por si otro admin agendó lo mismo entre la revisión y el insert.
            throw SqlErrors.translate(e, "No se pudo guardar la clase.");
        }
    }

    /** Deja vacío el bloque del grupo. */
    public void clear(int idGroup, Weekday day, TimeSlot slot, UserSession admin) throws ServiceException {
        Guards.requireAdmin(admin);
        try {
            db.actor(admin.idUser()).inTransaction(c -> {
                requireEditable(c, idGroup);
                return schedule.delete(c, idGroup, day, slot.idSlot());
            });
        } catch (Rejected e) {
            throw new ServiceException(e.getMessage());
        } catch (SQLException e) {
            throw SqlErrors.translate(e, "No se pudo quitar la clase.");
        }
    }

    private void requireEditable(java.sql.Connection c, int idGroup) throws SQLException {
        var g = groups.findById(c, idGroup).orElseThrow(() -> new Rejected("El grupo ya no existe. Recarga."));
        if (g.isHistory()) {
            throw new Rejected("El horario de " + g.name() + " (" + g.periodName() + ") es historial: el periodo ya cerró.");
        }
    }
}
