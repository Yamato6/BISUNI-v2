package mx.bisuni.desktop.service;

import mx.bisuni.desktop.dao.AnnouncementDao;
import mx.bisuni.desktop.dao.TeachingDao;
import mx.bisuni.desktop.db.Database;
import mx.bisuni.desktop.model.AnnouncementAudience;
import mx.bisuni.desktop.model.AnnouncementData;
import mx.bisuni.desktop.model.AnnouncementRow;
import mx.bisuni.desktop.model.Role;
import mx.bisuni.desktop.model.TeacherGroup;
import mx.bisuni.desktop.session.UserSession;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * Avisos que los alumnos ven en el portal.
 * <ul>
 *   <li>El admin publica para toda la escuela, una carrera o un grupo, y puede borrar cualquiera.</li>
 *   <li>El profesor solo publica para grupos del periodo activo donde da clase o es tutor, y solo
 *       ve y borra los suyos.</li>
 * </ul>
 */
public final class AnnouncementService {

    public static final int MAX_TITLE = 120;
    public static final int MAX_BODY = 2000;

    private final Database db;
    private final AnnouncementDao announcements;
    private final TeachingDao teaching;

    public AnnouncementService(Database db, AnnouncementDao announcements, TeachingDao teaching) {
        this.db = db;
        this.announcements = announcements;
        this.teaching = teaching;
    }

    /** El admin ve todos; el profesor, los suyos. */
    public List<AnnouncementRow> list(UserSession session) throws ServiceException {
        requireStaff(session);
        try {
            return db.withConnection(c -> announcements.list(c, session.isAdmin() ? null : session.idUser()));
        } catch (SQLException e) {
            throw new ServiceException("No se pudieron cargar los avisos.", e);
        }
    }

    /** Grupos a los que el profesor puede publicar (los suyos del periodo activo). */
    public List<TeacherGroup> teacherGroups(UserSession teacher) throws ServiceException {
        Guards.requireTeacher(teacher);
        try {
            return db.withConnection(c -> teaching.groups(c, teacher.profileId()));
        } catch (SQLException e) {
            throw new ServiceException("No se pudieron cargar tus grupos.", e);
        }
    }

    public int publish(AnnouncementData data, UserSession session) throws ServiceException {
        requireStaff(session);
        AnnouncementData d = validate(data);
        OffsetDateTime expires = d.expiresOn() == null ? null
                : d.expiresOn().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toOffsetDateTime();
        try {
            return db.actor(session.idUser()).inTransaction(c -> {
                if (!session.isAdmin()) {
                    if (d.audience() != AnnouncementAudience.Group) {
                        throw new Rejected("Como profesor solo puedes publicar avisos para tus grupos.");
                    }
                    boolean mine = teaching.groups(c, session.profileId()).stream()
                            .anyMatch(g -> g.idGroup() == d.idGroup());
                    if (!mine) {
                        throw new Rejected("Solo puedes publicar en grupos del periodo activo donde das clase o eres tutor.");
                    }
                }
                return announcements.insert(c, d, session.idUser(), expires);
            });
        } catch (Rejected e) {
            throw new ServiceException(e.getMessage());
        } catch (SQLException e) {
            throw SqlErrors.translate(e, "No se pudo publicar el aviso.");
        }
    }

    public void delete(int idAnnouncement, UserSession session) throws ServiceException {
        requireStaff(session);
        try {
            db.actor(session.idUser()).inTransaction(c -> {
                AnnouncementRow a = announcements.findById(c, idAnnouncement)
                        .orElseThrow(() -> new Rejected("El aviso ya no existe. Recarga la lista."));
                if (!session.isAdmin() && a.authorUser() != session.idUser()) {
                    throw new Rejected("Solo puedes borrar tus propios avisos.");
                }
                return announcements.delete(c, idAnnouncement);
            });
        } catch (Rejected e) {
            throw new ServiceException(e.getMessage());
        } catch (SQLException e) {
            throw SqlErrors.translate(e, "No se pudo borrar el aviso.");
        }
    }

    static AnnouncementData validate(AnnouncementData d) throws ServiceException {
        if (d == null || d.audience() == null) {
            throw new ServiceException("Elige a quién va dirigido el aviso.");
        }
        String title = Validation.required(d.title(), "el título", MAX_TITLE);
        String body = Validation.required(d.body(), "el texto del aviso", MAX_BODY);
        Integer career = d.audience() == AnnouncementAudience.Career ? d.idCareer() : null;
        Integer group = d.audience() == AnnouncementAudience.Group ? d.idGroup() : null;
        if (d.audience() == AnnouncementAudience.Career && career == null) {
            throw new ServiceException("Elige la carrera.");
        }
        if (d.audience() == AnnouncementAudience.Group && group == null) {
            throw new ServiceException("Elige el grupo.");
        }
        if (d.expiresOn() != null && d.expiresOn().isBefore(LocalDate.now())) {
            throw new ServiceException("La fecha de vencimiento ya pasó.");
        }
        return new AnnouncementData(title, body, d.audience(), career, group, d.expiresOn());
    }

    private static void requireStaff(UserSession session) throws ServiceException {
        if (session == null || (session.role() != Role.Admin && session.role() != Role.Teacher)) {
            throw new ServiceException("Solo el personal publica avisos.");
        }
    }
}
