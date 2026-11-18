package mx.bisuni.desktop.ui.groups;

import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;
import mx.bisuni.desktop.AppContext;
import mx.bisuni.desktop.model.GroupRow;
import mx.bisuni.desktop.model.Period;
import mx.bisuni.desktop.model.PeriodStatus;
import mx.bisuni.desktop.service.GroupService;
import mx.bisuni.desktop.service.ServiceException;
import mx.bisuni.desktop.session.UserSession;
import mx.bisuni.desktop.ui.Async;
import mx.bisuni.desktop.ui.Dialogs;
import mx.bisuni.desktop.ui.FormDialog;

import java.util.Comparator;
import java.util.List;

/**
 * Cierre de ciclo de un grupo de un periodo cerrado: reinscripción al semestre siguiente o, si
 * terminó el último semestre de la carrera, egreso.
 */
final class PromoteForm {

    private PromoteForm() {
    }

    /** Pregunta al servicio si toca egresar o reinscribir y muestra el diálogo correspondiente. */
    static void show(Node owner, AppContext ctx, UserSession session, GroupRow group, List<Period> periods,
                     Runnable onDone) {
        Async.run(() -> ctx.groups().isLastSemester(group.idGroup()),
                last -> {
                    if (last) {
                        graduate(owner, ctx, session, group, onDone);
                    } else {
                        promote(owner, ctx, session, group, periods, onDone);
                    }
                },
                error -> Dialogs.error(owner, error));
    }

    private static void graduate(Node owner, AppContext ctx, UserSession session, GroupRow group, Runnable onDone) {
        if (!Dialogs.confirm(owner, "Egreso de " + group.name(), group.name() + " (" + group.periodName()
                + ") terminó el último semestre de " + group.careerName() + ". Sus alumnos activos que no deben "
                + "materias pasarán a egresados. ¿Continuar?", "Registrar egreso")) {
            return;
        }
        Async.run(() -> ctx.groups().graduate(group.idGroup(), session),
                (GroupService.Graduation r) -> {
                    String msg = r.graduated() + (r.graduated() == 1 ? " alumno egresó." : " alumnos egresaron.");
                    if (!r.pending().isEmpty()) {
                        msg += "\n\nNo egresaron porque deben materias:\n  • " + String.join("\n  • ", r.pending());
                    }
                    Dialogs.info(owner, "Egreso de " + group.name(), msg);
                    onDone.run();
                },
                error -> Dialogs.error(owner, error));
    }

    private static void promote(Node owner, AppContext ctx, UserSession session, GroupRow group,
                                List<Period> periods, Runnable onDone) {
        List<Period> targets = periods.stream().filter(p -> !p.isClosed())
                .sorted(Comparator.comparing(Period::startDate)).toList();
        if (targets.isEmpty()) {
            Dialogs.info(owner, "Reinscripción", "No hay un periodo activo ni planeado al cual reinscribir. "
                    + "Créalo primero en Periodos.");
            return;
        }
        FormDialog form = new FormDialog(owner, "Reinscribir " + group.name() + " (" + group.periodName() + ")",
                "Reinscribir");
        form.addFull(FormDialog.note("Se crea el grupo de " + (group.semester() + 1) + "° semestre con el mismo tutor y "
                + "las materias de ese semestre (sin profesor), y se pasan sus " + group.studentCount()
                + " alumnos activos. Sus calificaciones quedan en el kárdex del grupo anterior."));
        ComboBox<Period> target = form.add("Periodo", new ComboBox<>());
        target.getItems().setAll(targets);
        target.setValue(targets.stream().filter(p -> p.status() == PeriodStatus.Active).findFirst()
                .orElse(targets.getFirst()));
        TextField name = form.add("Nuevo grupo", new TextField(suggestName(group.name(), group.semester() + 1)));
        GroupService.Promotion[] result = new GroupService.Promotion[1];
        boolean saved = form.showAndSave(() -> {
            if (target.getValue() == null) {
                throw new ServiceException("Elige el periodo.");
            }
            int idPeriod = target.getValue().idPeriod();
            String newName = name.getText();
            return () -> result[0] = ctx.groups().promote(group.idGroup(), idPeriod, newName, session);
        });
        if (saved) {
            var r = result[0];
            Dialogs.info(owner, "Reinscripción", r.students() + (r.students() == 1 ? " alumno quedó" : " alumnos quedaron")
                    + " en " + r.groupName() + " (" + r.periodName() + ") con " + r.subjects()
                    + (r.subjects() == 1 ? " materia" : " materias")
                    + ". Falta asignar profesores y armar su horario.");
            onDone.run();
        }
    }

    /** "IT-1A" en 2° semestre → "IT-2A": cambia el primer número por el semestre nuevo. */
    static String suggestName(String name, int semester) {
        return name.matches(".*\\d.*") ? name.replaceFirst("\\d+", String.valueOf(semester)) : name + "-" + semester;
    }
}
