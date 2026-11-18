package mx.bisuni.desktop.dev;

import mx.bisuni.desktop.AppContext;
import mx.bisuni.desktop.config.AppConfig;
import mx.bisuni.desktop.db.Database;
import mx.bisuni.desktop.model.AnnouncementAudience;
import mx.bisuni.desktop.model.AnnouncementData;
import mx.bisuni.desktop.model.AspirantStatus;
import mx.bisuni.desktop.model.AttendanceStatus;
import mx.bisuni.desktop.model.GradeInput;
import mx.bisuni.desktop.model.StudentRow;
import mx.bisuni.desktop.model.TeacherClass;
import mx.bisuni.desktop.service.AdmissionService;
import mx.bisuni.desktop.service.ServiceException;
import mx.bisuni.desktop.session.UserSession;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Ejecuta acciones del escritorio usando sus servicios REALES (los mismos que usa la UI), para
 * la prueba E2E del flujo completo entre web y escritorio. Toma la conexión de AppConfig
 * (variables de entorno DB_* > bisuni.properties > .env), así la prueba lo apunta a su base.
 * <pre>
 *   accept         &lt;adminEmail&gt; &lt;password&gt; &lt;folio&gt;
 *   assign-group   &lt;adminEmail&gt; &lt;password&gt; &lt;studentEmail&gt; &lt;groupName&gt;
 *   grade          &lt;teacherEmail&gt; &lt;password&gt; &lt;studentEmail&gt; &lt;groupName&gt; &lt;idSubject&gt; &lt;p1&gt; &lt;p2&gt; &lt;p3&gt;
 *   grade-rest     &lt;teacherEmail&gt; &lt;password&gt; &lt;groupName&gt; &lt;idSubject&gt; &lt;p1&gt; &lt;p2&gt; &lt;p3&gt;
 *   close-acta     &lt;teacherEmail&gt; &lt;password&gt; &lt;groupName&gt; &lt;idSubject&gt;
 *   attendance     &lt;teacherEmail&gt; &lt;password&gt; &lt;studentEmail&gt; &lt;groupName&gt; &lt;idSubject&gt; &lt;AAAA-MM-DD&gt; &lt;Present|Absent|Late|Excused&gt;
 *   announce       &lt;email&gt; &lt;password&gt; &lt;groupName&gt; &lt;título&gt; &lt;texto&gt;      (guion bajo = espacio)
 *   reset-password &lt;adminEmail&gt; &lt;password&gt; &lt;userEmail&gt;                 (imprime la temporal)
 * </pre>
 * Varios comandos se encadenan con {@code +} en una sola ejecución (la JVM arranca una vez).
 * Cada comando imprime {@code OK ...}; si un servicio lo rechaza imprime {@code RECHAZADO mensaje}
 * y termina con código 1 sin ejecutar los siguientes.
 */
public final class DesktopCli {

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            usage();
        }
        List<String[]> commands = new ArrayList<>();
        List<String> current = new ArrayList<>();
        for (String a : args) {
            if (a.equals("+")) {
                commands.add(current.toArray(String[]::new));
                current.clear();
            } else {
                current.add(a);
            }
        }
        commands.add(current.toArray(String[]::new));

        try (Database db = Database.connect(AppConfig.load())) {
            AppContext ctx = AppContext.wire(db);
            for (String[] cmd : commands) {
                if (cmd.length == 0) {
                    usage();
                }
                String result = switch (cmd[0]) {
                    case "accept" -> accept(ctx, cmd);
                    case "assign-group" -> assignGroup(ctx, cmd);
                    case "grade" -> grade(ctx, cmd);
                    case "grade-rest" -> gradeRest(ctx, cmd);
                    case "close-acta" -> closeActa(ctx, cmd);
                    case "attendance" -> attendance(ctx, cmd);
                    case "announce" -> announce(ctx, cmd);
                    case "reset-password" -> resetPassword(ctx, cmd);
                    default -> {
                        usage();
                        yield "";
                    }
                };
                System.out.println("OK " + cmd[0] + " " + result);
            }
        } catch (ServiceException e) {
            System.out.println("RECHAZADO " + e.getMessage());
            System.exit(1);
        }
    }

    private static UserSession login(AppContext ctx, String[] a) throws ServiceException {
        return ctx.auth().login(a[1], a[2].toCharArray());
    }

    private static String accept(AppContext ctx, String[] a) throws ServiceException {
        need(a, 4);
        UserSession admin = login(ctx, a);
        int idAspirant = Integer.parseInt(a[3].replaceFirst("(?i)^ASP-", ""));
        var result = ctx.admissions().applyChanges(
                Map.of(idAspirant, new AdmissionService.Change(AspirantStatus.Pending, AspirantStatus.Accepted)), admin);
        return "aceptados=" + result.accepted();
    }

    private static String assignGroup(AppContext ctx, String[] a) throws ServiceException {
        need(a, 5);
        UserSession admin = login(ctx, a);
        StudentRow student = studentByEmail(ctx, a[3]);
        var group = ctx.groups().list(null).stream().filter(g -> g.name().equals(a[4]) && !g.isHistory()).findFirst()
                .orElseThrow(() -> new ServiceException("No existe el grupo " + a[4]));
        ctx.groups().addStudent(group.idGroup(), student.idStudent(), admin);
        return "alumno=" + student.idStudent() + " grupo=" + group.idGroup();
    }

    private static String grade(AppContext ctx, String[] a) throws ServiceException {
        need(a, 9);
        UserSession teacher = login(ctx, a);
        StudentRow student = studentByEmail(ctx, a[3]);
        TeacherClass cls = teacherClass(ctx, teacher, a[4], a[5]);
        int saved = ctx.grades().save(cls.idGroup(), cls.idSubject(), List.of(new GradeInput(student.idStudent(),
                new BigDecimal(a[6]), new BigDecimal(a[7]), new BigDecimal(a[8]))), teacher);
        return "guardadas=" + saved;
    }

    /** Califica con los mismos parciales a todos los alumnos del grupo que aún no los tienen completos. */
    private static String gradeRest(AppContext ctx, String[] a) throws ServiceException {
        need(a, 8);
        UserSession teacher = login(ctx, a);
        TeacherClass cls = teacherClass(ctx, teacher, a[3], a[4]);
        List<GradeInput> rest = ctx.grades().sheet(cls.idGroup(), cls.idSubject(), teacher).stream()
                .filter(r -> r.inGroup() && r.finalGrade() == null)
                .map(r -> new GradeInput(r.idStudent(), new BigDecimal(a[5]), new BigDecimal(a[6]), new BigDecimal(a[7])))
                .toList();
        return "guardadas=" + ctx.grades().save(cls.idGroup(), cls.idSubject(), rest, teacher);
    }

    private static String closeActa(AppContext ctx, String[] a) throws ServiceException {
        need(a, 5);
        UserSession teacher = login(ctx, a);
        TeacherClass cls = teacherClass(ctx, teacher, a[3], a[4]);
        ctx.grades().closeActa(cls.idGroup(), cls.idSubject(), teacher);
        return "acta=" + cls.label();
    }

    private static String attendance(AppContext ctx, String[] a) throws ServiceException {
        need(a, 8);
        UserSession teacher = login(ctx, a);
        StudentRow student = studentByEmail(ctx, a[3]);
        TeacherClass cls = teacherClass(ctx, teacher, a[4], a[5]);
        int saved = ctx.attendance().save(cls.idGroup(), cls.idSubject(), LocalDate.parse(a[6]),
                Map.of(student.idStudent(), AttendanceStatus.valueOf(a[7])), teacher);
        return "marcas=" + saved;
    }

    private static String announce(AppContext ctx, String[] a) throws ServiceException {
        need(a, 6);
        UserSession author = login(ctx, a);
        var group = ctx.groups().list(null).stream().filter(g -> g.name().equals(a[3]) && !g.isHistory()).findFirst()
                .orElseThrow(() -> new ServiceException("No existe el grupo " + a[3]));
        int id = ctx.announcements().publish(new AnnouncementData(a[4].replace('_', ' '), a[5].replace('_', ' '),
                AnnouncementAudience.Group, null, group.idGroup(), null), author);
        return "aviso=" + id;
    }

    private static String resetPassword(AppContext ctx, String[] a) throws ServiceException {
        need(a, 4);
        UserSession admin = login(ctx, a);
        var user = ctx.users().list(null, null, a[3]).stream().filter(u -> u.email().equalsIgnoreCase(a[3])).findFirst()
                .orElseThrow(() -> new ServiceException("No hay usuario con el correo " + a[3]));
        return "temporal=" + ctx.users().resetPassword(user.idUser(), admin);
    }

    private static TeacherClass teacherClass(AppContext ctx, UserSession teacher, String groupName, String idSubject)
            throws ServiceException {
        int subject = Integer.parseInt(idSubject);
        return ctx.teaching().myClasses(teacher).stream()
                .filter(c -> c.groupName().equals(groupName) && c.idSubject() == subject)
                .findFirst()
                .orElseThrow(() -> new ServiceException("El profesor no imparte la materia " + idSubject + " en " + groupName));
    }

    private static StudentRow studentByEmail(AppContext ctx, String email) throws ServiceException {
        return ctx.students().list(new mx.bisuni.desktop.dao.StudentDao.Filter(null, null, false, null, email)).stream()
                .filter(s -> s.email().equalsIgnoreCase(email))
                .findFirst()
                .orElseThrow(() -> new ServiceException("No hay alumno con el correo " + email));
    }

    private static void need(String[] a, int n) {
        if (a.length < n) {
            System.err.println("Faltan argumentos en: " + Arrays.toString(a));
            usage();
        }
    }

    private static void usage() {
        System.err.println("""
                Uso (varios comandos se separan con +):
                  accept         <adminEmail> <password> <folio>
                  assign-group   <adminEmail> <password> <studentEmail> <groupName>
                  grade          <teacherEmail> <password> <studentEmail> <groupName> <idSubject> <p1> <p2> <p3>
                  grade-rest     <teacherEmail> <password> <groupName> <idSubject> <p1> <p2> <p3>
                  close-acta     <teacherEmail> <password> <groupName> <idSubject>
                  attendance     <teacherEmail> <password> <studentEmail> <groupName> <idSubject> <AAAA-MM-DD> <estatus>
                  announce       <email> <password> <groupName> <título> <texto>   (guion bajo = espacio)
                  reset-password <adminEmail> <password> <userEmail>""");
        System.exit(2);
    }
}
