package mx.bisuni.desktop.service;

import mx.bisuni.desktop.dao.AnnouncementDao;
import mx.bisuni.desktop.dao.TeachingDao;
import mx.bisuni.desktop.db.Database;
import mx.bisuni.desktop.model.AnnouncementAudience;
import mx.bisuni.desktop.model.AnnouncementData;
import mx.bisuni.desktop.model.Role;
import mx.bisuni.desktop.session.UserSession;
import mx.bisuni.desktop.support.PostgresTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static mx.bisuni.desktop.support.Sql.text;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Seed: 4 avisos (uno vencido); el de IT-1A es de Juan Pérez. */
class AnnouncementServiceTest {

    private static final UserSession ADMIN =
            new UserSession(1, "admin.master@bisuni.edu", "Antonio", "Ramírez", Role.Admin, 1);
    private static final UserSession JUAN =
            new UserSession(3, "juan.perez@bisuni.edu", "Juan", "Pérez", Role.Teacher, 1);

    private Database db;
    private AnnouncementService service;

    @BeforeEach
    void setUp() {
        db = PostgresTestSupport.freshDatabase();
        service = new AnnouncementService(db, new AnnouncementDao(), new TeachingDao());
    }

    private static AnnouncementData data(AnnouncementAudience a, Integer career, Integer group) {
        return new AnnouncementData("Aviso de prueba", "Texto del aviso.", a, career, group, LocalDate.now().plusDays(7));
    }

    @Test
    void adminSeesAllAndTeacherOnlyTheirs() throws Exception {
        var all = service.list(ADMIN);
        assertEquals(4, all.size());
        assertEquals(1, all.stream().filter(a -> a.expired()).count());
        var juan = service.list(JUAN);
        assertEquals(1, juan.size());
        assertEquals("Grupo: IT-1A (2026-2)", juan.getFirst().audienceText());
    }

    @Test
    void teacherPublishesOnlyToTheirGroups() throws Exception {
        assertTrue(service.teacherGroups(JUAN).stream().anyMatch(g -> g.idGroup() == 1));
        int id = service.publish(data(AnnouncementAudience.Group, null, 1), JUAN);
        assertEquals("Group|1|3", text(db, "SELECT audience || '|' || id_group || '|' || author_user "
                + "FROM announcement WHERE id_announcement = ?", id));

        var other = assertThrows(ServiceException.class, () -> service.publish(data(AnnouncementAudience.Group, null, 3), JUAN));
        assertTrue(other.getMessage().contains("donde das clase"), other.getMessage());
        var all = assertThrows(ServiceException.class, () -> service.publish(data(AnnouncementAudience.All, null, null), JUAN));
        assertEquals("Como profesor solo puedes publicar avisos para tus grupos.", all.getMessage());
    }

    @Test
    void adminPublishesToCareerAndValidates() throws Exception {
        int id = service.publish(data(AnnouncementAudience.Career, 2, 99), ADMIN);
        assertEquals("Career|2|", text(db, "SELECT audience || '|' || id_career || '|' || coalesce(id_group::text, '') "
                + "FROM announcement WHERE id_announcement = ?", id), "el grupo se ignora si el aviso es para una carrera");
        assertThrows(ServiceException.class, () -> service.publish(data(AnnouncementAudience.Career, null, null), ADMIN));
        assertThrows(ServiceException.class, () -> service.publish(
                new AnnouncementData("  ", "x", AnnouncementAudience.All, null, null, null), ADMIN));
        var past = assertThrows(ServiceException.class, () -> service.publish(
                new AnnouncementData("T", "x", AnnouncementAudience.All, null, null, LocalDate.now().minusDays(1)), ADMIN));
        assertEquals("La fecha de vencimiento ya pasó.", past.getMessage());
    }

    @Test
    void deletingOwnOrAsAdmin() throws Exception {
        int adminNotice = service.list(ADMIN).stream().filter(a -> a.authorUser() == 1).findFirst().orElseThrow().idAnnouncement();
        var e = assertThrows(ServiceException.class, () -> service.delete(adminNotice, JUAN));
        assertEquals("Solo puedes borrar tus propios avisos.", e.getMessage());
        int mine = service.list(JUAN).getFirst().idAnnouncement();
        service.delete(mine, JUAN);
        service.delete(adminNotice, ADMIN);
        assertEquals(2, service.list(ADMIN).size());
    }
}
