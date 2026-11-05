package mx.bisuni.desktop.ui.students;

import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextField;
import mx.bisuni.desktop.AppContext;
import mx.bisuni.desktop.model.Catalog;
import mx.bisuni.desktop.model.StudentRow;
import mx.bisuni.desktop.model.StudentUpdate;
import mx.bisuni.desktop.service.ServiceException;
import mx.bisuni.desktop.session.UserSession;
import mx.bisuni.desktop.ui.FormDialog;
import mx.bisuni.desktop.ui.Option;
import mx.bisuni.desktop.ui.UserFields;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Formulario de edición de un alumno: datos de usuario y perfil académico. */
final class StudentForm {

    private StudentForm() {
    }

    static boolean edit(Node owner, AppContext ctx, UserSession session, StudentsController.Catalogs catalogs,
                        StudentRow student) {
        FormDialog form = new FormDialog(owner, "Editar alumno", "Guardar");
        UserFields fields = new UserFields(form);
        fields.fill(student.userData());

        form.section("Perfil académico");
        ComboBox<Catalog.Career> career = form.add("Carrera", new ComboBox<>());
        career.getItems().setAll(catalogs.careers());
        Spinner<Integer> semester = form.add("Semestre", new Spinner<>(1, 12, student.semester()));
        ComboBox<Option<Catalog.Group>> group = form.add("Grupo", new ComboBox<>());
        TextField gpa = form.add("Promedio", new TextField(student.gpa().toPlainString()));
        gpa.setPromptText("0 a 10, máximo 2 decimales");
        form.addFull(FormDialog.note("El grupo debe ser de la misma carrera del alumno. "
                + "Cambiar de grupo no borra calificaciones: cada una queda ligada al grupo donde se cursó."));

        // La carrera puede estar inactiva y no venir en el catálogo; en ese caso se agrega para no perderla.
        Catalog.Career currentCareer = catalogs.careers().stream()
                .filter(c -> c.idCareer() == student.idCareer()).findFirst()
                .orElseGet(() -> {
                    Catalog.Career c = new Catalog.Career(student.idCareer(), student.careerName());
                    career.getItems().add(c);
                    return c;
                });

        career.valueProperty().addListener((obs, old, c) -> {
            Integer keep = old == null ? student.idGroup() : null; // al cambiar de carrera el grupo se reinicia
            fillGroups(group, catalogs, c, keep, student);
        });
        career.setValue(currentCareer);

        return form.showAndSave(() -> {
            if (career.getValue() == null) {
                throw new ServiceException("Selecciona la carrera.");
            }
            BigDecimal parsedGpa;
            try {
                parsedGpa = new BigDecimal(gpa.getText().strip().replace(',', '.'));
            } catch (NumberFormatException e) {
                throw new ServiceException("El promedio debe ser un número entre 0 y 10.");
            }
            Catalog.Group g = group.getValue() == null ? null : group.getValue().value();
            var update = new StudentUpdate(fields.read(), career.getValue().idCareer(), semester.getValue(),
                    parsedGpa, g == null ? null : g.idGroup());
            return () -> {
                ctx.students().update(student.idStudent(), update, session);
                return null;
            };
        });
    }

    private static void fillGroups(ComboBox<Option<Catalog.Group>> combo, StudentsController.Catalogs catalogs,
                                   Catalog.Career career, Integer selectId, StudentRow student) {
        List<Option<Catalog.Group>> options = new ArrayList<>();
        options.add(new Option<>("Sin grupo", null));
        if (career != null) {
            for (Catalog.Group g : catalogs.groupsOf(career.idCareer())) {
                options.add(new Option<>(g.toString(), g));
            }
            // Grupo actual inactivo (no viene en el catálogo): se conserva como opción.
            if (selectId != null && options.stream().noneMatch(o -> o.value() != null
                    && Objects.equals(o.value().idGroup(), selectId))) {
                options.add(new Option<>(student.groupName(),
                        new Catalog.Group(selectId, student.groupName(), career.idCareer(), student.semester())));
            }
        }
        combo.getItems().setAll(options);
        combo.getSelectionModel().select(options.stream()
                .filter(o -> o.value() != null && Objects.equals(o.value().idGroup(), selectId))
                .findFirst().orElse(options.getFirst()));
    }
}
