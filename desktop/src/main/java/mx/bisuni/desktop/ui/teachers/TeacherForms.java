package mx.bisuni.desktop.ui.teachers;

import javafx.scene.Node;
import javafx.scene.control.TextField;
import mx.bisuni.desktop.AppContext;
import mx.bisuni.desktop.model.TeacherRow;
import mx.bisuni.desktop.session.UserSession;
import mx.bisuni.desktop.ui.FormDialog;
import mx.bisuni.desktop.ui.UserFields;

/** Formulario de edición del perfil de un profesor. */
final class TeacherForms {

    private TeacherForms() {
    }

    static boolean edit(Node owner, AppContext ctx, UserSession session, TeacherRow teacher) {
        FormDialog form = new FormDialog(owner, "Editar profesor", "Guardar");
        UserFields fields = new UserFields(form);
        fields.fill(teacher.userData());

        form.section("Perfil docente");
        TextField specialty = form.add("Especialidad", new TextField(teacher.specialty()));
        TextField phone = form.add("Teléfono", new TextField(teacher.phone()));
        phone.setPromptText("p. ej. +52-449-555-1000");

        return form.showAndSave(() -> {
            var data = fields.read();
            String sp = specialty.getText();
            String ph = phone.getText();
            return () -> {
                ctx.teachers().update(teacher.idTeacher(), data, sp, ph, session);
                return null;
            };
        });
    }
}
