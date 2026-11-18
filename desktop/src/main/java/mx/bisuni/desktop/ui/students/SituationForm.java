package mx.bisuni.desktop.ui.students;

import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import mx.bisuni.desktop.AppContext;
import mx.bisuni.desktop.model.StudentRow;
import mx.bisuni.desktop.model.StudentStatus;
import mx.bisuni.desktop.service.ServiceException;
import mx.bisuni.desktop.session.UserSession;
import mx.bisuni.desktop.ui.Dialogs;
import mx.bisuni.desktop.ui.FormDialog;

import java.util.Arrays;

/** Cambio de situación académica: baja temporal o definitiva, reingreso o egreso. */
final class SituationForm {

    private SituationForm() {
    }

    static boolean show(Node owner, AppContext ctx, UserSession session, StudentRow student) {
        var targets = Arrays.stream(StudentStatus.values()).filter(student.academicStatus()::canChangeTo).toList();
        if (targets.isEmpty()) {
            Dialogs.info(owner, "Situación del alumno", student.firstName() + " " + student.lastName()
                    + " ya egresó; el egreso es definitivo.");
            return false;
        }
        FormDialog form = new FormDialog(owner, "Situación de " + student.firstName() + " " + student.lastName(),
                "Cambiar");
        form.addFull(FormDialog.note("Matrícula " + student.enrollmentNumber() + " · situación actual: "
                + student.academicStatus().label().toLowerCase()
                + (student.groupName() == null ? "" : " · grupo " + student.groupName())));
        ComboBox<StudentStatus> target = form.add("Nueva situación", new ComboBox<>());
        target.getItems().setAll(targets);
        target.getSelectionModel().selectFirst();
        form.addFull(FormDialog.note("Una baja (temporal o definitiva) saca al alumno de su grupo; sus calificaciones "
                + "se conservan en el kárdex. Con baja definitiva ya no puede entrar al portal. Al reactivarlo queda sin "
                + "grupo: asígnalo en Grupos. Solo egresa quien no debe materias."));
        return form.showAndSave(() -> {
            StudentStatus chosen = target.getValue();
            if (chosen == null) {
                throw new ServiceException("Elige la nueva situación.");
            }
            return () -> {
                ctx.students().changeAcademicStatus(student.idStudent(), chosen, session);
                return null;
            };
        });
    }
}
