package mx.bisuni.desktop.ui.subjects;

import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextField;
import mx.bisuni.desktop.AppContext;
import mx.bisuni.desktop.model.Catalog;
import mx.bisuni.desktop.model.RecordStatus;
import mx.bisuni.desktop.model.SubjectData;
import mx.bisuni.desktop.model.SubjectRow;
import mx.bisuni.desktop.service.ServiceException;
import mx.bisuni.desktop.service.SubjectService;
import mx.bisuni.desktop.session.UserSession;
import mx.bisuni.desktop.ui.FormDialog;

import java.util.List;

/** Alta ({@code subject == null}) o edición de una materia. */
final class SubjectForm {

    private SubjectForm() {
    }

    static boolean show(Node owner, AppContext ctx, UserSession session, List<Catalog.Career> careers,
                        SubjectRow subject) {
        boolean creating = subject == null;
        FormDialog form = new FormDialog(owner, creating ? "Nueva materia" : "Editar materia",
                creating ? "Crear materia" : "Guardar");
        TextField name = form.add("Nombre", new TextField(creating ? "" : subject.name()));
        ComboBox<Catalog.Career> career = form.add("Carrera", new ComboBox<>());
        career.getItems().setAll(careers);
        Spinner<Integer> semester = form.add("Semestre", new Spinner<>(1, 12, creating ? 1 : subject.semester()));
        Spinner<Integer> credits = form.add("Créditos",
                new Spinner<>(1, SubjectService.MAX_CREDITS, creating ? 5 : subject.credits()));
        Spinner<Integer> hours = form.add("Horas por semana",
                new Spinner<>(1, SubjectService.MAX_WEEKLY_HOURS, creating ? 3 : subject.weeklyHours()));
        ComboBox<RecordStatus> status = form.add("Estatus", new ComboBox<>());
        status.getItems().setAll(RecordStatus.values());
        status.setValue(creating ? RecordStatus.Active : subject.status());

        if (!creating) {
            Catalog.Career current = careers.stream().filter(c -> c.idCareer() == subject.idCareer()).findFirst()
                    .orElseGet(() -> {
                        var c = new Catalog.Career(subject.idCareer(), subject.careerName());
                        career.getItems().add(c);
                        return c;
                    });
            career.setValue(current);
            if (subject.groupCount() > 0) {
                career.setDisable(true);
                form.addFull(FormDialog.note("La carrera no se puede cambiar porque la materia ya está asignada a "
                        + subject.groupCount() + (subject.groupCount() == 1 ? " grupo." : " grupos.")));
            }
        }
        form.addFull(FormDialog.note("Las horas por semana son las que la materia debe ocupar en el horario de cada grupo."));

        return form.showAndSave(() -> {
            if (career.getValue() == null) {
                throw new ServiceException("Selecciona la carrera.");
            }
            var data = new SubjectData(name.getText(), career.getValue().idCareer(), semester.getValue(),
                    credits.getValue(), hours.getValue(), status.getValue());
            return () -> {
                if (creating) {
                    ctx.subjects().create(data, session);
                } else {
                    ctx.subjects().update(subject.idSubject(), data, session);
                }
                return null;
            };
        });
    }
}
