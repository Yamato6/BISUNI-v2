package mx.bisuni.desktop.ui.groups;

import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextField;
import mx.bisuni.desktop.AppContext;
import mx.bisuni.desktop.model.Catalog;
import mx.bisuni.desktop.model.GroupData;
import mx.bisuni.desktop.model.GroupRow;
import mx.bisuni.desktop.model.RecordStatus;
import mx.bisuni.desktop.service.ServiceException;
import mx.bisuni.desktop.session.UserSession;
import mx.bisuni.desktop.ui.FormDialog;
import mx.bisuni.desktop.ui.Option;

import java.util.ArrayList;
import java.util.List;

/** Alta ({@code group == null}) o edición de los datos generales de un grupo. */
final class GroupForm {

    private GroupForm() {
    }

    static boolean show(Node owner, AppContext ctx, UserSession session, GroupsController.Catalogs catalogs,
                        GroupRow group) {
        boolean creating = group == null;
        FormDialog form = new FormDialog(owner, creating ? "Nuevo grupo" : "Editar grupo",
                creating ? "Crear grupo" : "Guardar");
        TextField name = form.add("Nombre", new TextField(creating ? "" : group.name()));
        name.setPromptText("p. ej. IT-2A");
        ComboBox<Catalog.Career> career = form.add("Carrera", new ComboBox<>());
        career.getItems().setAll(catalogs.careers());
        Spinner<Integer> semester = form.add("Semestre", new Spinner<>(1, 12, creating ? 1 : group.semester()));
        ComboBox<Option<Catalog.Teacher>> tutor = form.add("Tutor", new ComboBox<>());
        ComboBox<RecordStatus> status = form.add("Estatus", new ComboBox<>());
        status.getItems().setAll(RecordStatus.values());
        status.setValue(creating ? RecordStatus.Active : group.status());

        List<Option<Catalog.Teacher>> tutors = new ArrayList<>();
        tutors.add(new Option<>("Sin tutor", null));
        catalogs.teachers().forEach(t -> tutors.add(new Option<>(t.name(), t)));
        // Un tutor inactivo no viene en el catálogo; se conserva para no borrarlo sin querer.
        if (!creating && group.idTutor() != null
                && catalogs.teachers().stream().noneMatch(t -> t.idTeacher() == group.idTutor())) {
            tutors.add(new Option<>(group.tutorName() + " (inactivo)",
                    new Catalog.Teacher(group.idTutor(), group.tutorName())));
        }
        tutor.getItems().setAll(tutors);
        tutor.setValue(tutors.stream()
                .filter(o -> o.value() != null && !creating && group.idTutor() != null
                        && o.value().idTeacher() == group.idTutor())
                .findFirst().orElse(tutors.getFirst()));

        if (!creating) {
            career.setValue(catalogs.careers().stream().filter(c -> c.idCareer() == group.idCareer()).findFirst()
                    .orElseGet(() -> {
                        var c = new Catalog.Career(group.idCareer(), group.careerName());
                        career.getItems().add(c);
                        return c;
                    }));
            if (group.studentCount() > 0 || group.subjectCount() > 0) {
                career.setDisable(true);
                form.addFull(FormDialog.note(
                        "La carrera no se puede cambiar porque el grupo ya tiene alumnos o materias."));
            }
        }
        form.addFull(FormDialog.note("El nombre se guarda en mayúsculas. Las materias y los alumnos se "
                + "administran con «Materias y alumnos…»."));

        return form.showAndSave(() -> {
            if (career.getValue() == null) {
                throw new ServiceException("Selecciona la carrera.");
            }
            Catalog.Teacher t = tutor.getValue() == null ? null : tutor.getValue().value();
            var data = new GroupData(name.getText(), career.getValue().idCareer(), semester.getValue(),
                    t == null ? null : t.idTeacher(), status.getValue());
            return () -> {
                if (creating) {
                    ctx.groups().create(data, session);
                } else {
                    ctx.groups().update(group.idGroup(), data, session);
                }
                return null;
            };
        });
    }
}
