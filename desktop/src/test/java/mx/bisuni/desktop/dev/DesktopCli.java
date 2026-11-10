package mx.bisuni.desktop.dev;

import mx.bisuni.desktop.AppContext;
import mx.bisuni.desktop.config.AppConfig;
import mx.bisuni.desktop.db.Database;
import mx.bisuni.desktop.model.AspirantStatus;
import mx.bisuni.desktop.model.GradeInput;
import mx.bisuni.desktop.model.StudentRow;
import mx.bisuni.desktop.service.AdmissionService;
import mx.bisuni.desktop.service.ServiceException;
import mx.bisuni.desktop.session.UserSession;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Ejecuta acciones del escritorio usando sus servicios REALES (los mismos que usa la UI), para
 * la prueba E2E del flujo completo entre web y escritorio. Toma la conexión de AppConfig
 * (variables de entorno DB_* > bisuni.properties > .env), así la prueba lo apunta a su base.
 * <pre>
 *   accept       &lt;adminEmail&gt; &lt;password&gt; &lt;folio&gt;
 *   assign-group &lt;adminEmail&gt; &lt;password&gt; &lt;studentEmail&gt; &lt;groupName&gt;
 *   grade        &lt;teacherEmail&gt; &lt;password&gt; &lt;studentEmail&gt; &lt;groupName&gt; &lt;idSubject&gt; &lt;p1&gt; &lt;p2&gt; &lt;p3&gt;
 * </pre>
 * Termina con código 0 si la acción se aplicó; con 1 e imprime el mensaje si el servicio la rechazó.
 */
public final class DesktopCli {

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            usage();
        }
        try (Database db = Database.connect(AppConfig.load())) {
            AppContext ctx = AppContext.wire(db);
            String result = switch (args[0]) {
                case "accept" -> accept(ctx, args);
                case "assign-group" -> assignGroup(ctx, args);
                case "grade" -> grade(ctx, args);
                default -> {
                    usage();
                    yield "";
                }
            };
            System.out.println("OK " + result);
        } catch (ServiceException e) {
            System.out.println("RECHAZADO " + e.getMessage());
            System.exit(1);
        }
    }

    private static String accept(AppContext ctx, String[] a) throws ServiceException {
        need(a, 4);
        UserSession admin = ctx.auth().login(a[1], a[2].toCharArray());
        int idAspirant = Integer.parseInt(a[3].replaceFirst("(?i)^ASP-", ""));
        var result = ctx.admissions().applyChanges(
                Map.of(idAspirant, new AdmissionService.Change(AspirantStatus.Pending, AspirantStatus.Accepted)), admin);
        return "aceptados=" + result.accepted();
    }

    private static String assignGroup(AppContext ctx, String[] a) throws ServiceException {
        need(a, 5);
        UserSession admin = ctx.auth().login(a[1], a[2].toCharArray());
        StudentRow student = studentByEmail(ctx, a[3]);
        var group = ctx.groups().list(null).stream().filter(g -> g.name().equals(a[4])).findFirst()
                .orElseThrow(() -> new ServiceException("No existe el grupo " + a[4]));
        ctx.groups().addStudent(group.idGroup(), student.idStudent(), admin);
        return "alumno=" + student.idStudent() + " grupo=" + group.idGroup();
    }

    private static String grade(AppContext ctx, String[] a) throws ServiceException {
        need(a, 9);
        UserSession teacher = ctx.auth().login(a[1], a[2].toCharArray());
        StudentRow student = studentByEmail(ctx, a[3]);
        var cls = ctx.teaching().myClasses(teacher).stream()
                .filter(c -> c.groupName().equals(a[4]) && c.idSubject() == Integer.parseInt(a[5]))
                .findFirst()
                .orElseThrow(() -> new ServiceException("El profesor no imparte la materia " + a[5] + " en " + a[4]));
        int saved = ctx.grades().save(cls.idGroup(), cls.idSubject(), List.of(new GradeInput(student.idStudent(),
                new BigDecimal(a[6]), new BigDecimal(a[7]), new BigDecimal(a[8]))), teacher);
        return "guardadas=" + saved;
    }

    private static StudentRow studentByEmail(AppContext ctx, String email) throws ServiceException {
        return ctx.students().list(new mx.bisuni.desktop.dao.StudentDao.Filter(null, null, false, null, email)).stream()
                .filter(s -> s.email().equalsIgnoreCase(email))
                .findFirst()
                .orElseThrow(() -> new ServiceException("No hay alumno con el correo " + email));
    }

    private static void need(String[] a, int n) {
        if (a.length < n) {
            usage();
        }
    }

    private static void usage() {
        System.err.println("""
                Uso:
                  accept       <adminEmail> <password> <folio>
                  assign-group <adminEmail> <password> <studentEmail> <groupName>
                  grade        <teacherEmail> <password> <studentEmail> <groupName> <idSubject> <p1> <p2> <p3>""");
        System.exit(2);
    }
}
