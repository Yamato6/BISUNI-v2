package mx.bisuni.desktop.ui.users;

import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.ColumnConstraints;
import mx.bisuni.desktop.AppContext;
import mx.bisuni.desktop.model.Catalog;
import mx.bisuni.desktop.model.ProfileData;
import mx.bisuni.desktop.model.Role;
import mx.bisuni.desktop.model.UserRow;
import mx.bisuni.desktop.service.ServiceException;
import mx.bisuni.desktop.session.UserSession;
import mx.bisuni.desktop.ui.Async;
import mx.bisuni.desktop.ui.Dialogs;
import mx.bisuni.desktop.ui.FormDialog;
import mx.bisuni.desktop.ui.UserFields;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

/** Formularios de alta y edición de usuarios. */
final class UserForms {

    private UserForms() {
    }

    /** Carga el catálogo de carreras en segundo plano y abre el alta; llama a {@code onCreated} si se guardó. */
    static void create(Node owner, AppContext ctx, UserSession session, Runnable onCreated) {
        Async.run(() -> ctx.catalog().careers(),
                careers -> {
                    if (create(owner, ctx, session, careers).isPresent()) {
                        onCreated.run();
                    }
                },
                error -> Dialogs.error(owner, error));
    }

    /** Alta de usuario con su perfil. Devuelve el id creado, o vacío si se canceló. */
    private static Optional<Integer> create(Node owner, AppContext ctx, UserSession session,
                                            List<Catalog.Career> careers) {
        FormDialog form = new FormDialog(owner, "Nuevo usuario", "Crear usuario");
        ComboBox<Role> role = form.add("Rol", new ComboBox<>());
        role.getItems().setAll(Role.Admin, Role.Teacher, Role.Student);
        UserFields fields = new UserFields(form);
        fields.addPassword(form, true);

        // Datos del perfil: cambian según el rol elegido.
        form.section("Datos del perfil");
        GridPane profile = form.addFull(profileGrid());
        TextField position = new TextField();
        TextField specialty = new TextField();
        TextField phone = new TextField();
        ComboBox<Catalog.Career> career = new ComboBox<>();
        career.getItems().setAll(careers);
        career.setMaxWidth(Double.MAX_VALUE);
        Spinner<Integer> semester = new Spinner<>(1, 12, 1);
        Label hint = FormDialog.note("");

        role.valueProperty().addListener((obs, old, r) -> {
            profile.getChildren().clear();
            if (r == null) {
                return;
            }
            switch (r) {
                case Admin -> row(profile, 0, "Puesto", position);
                case Teacher -> {
                    row(profile, 0, "Especialidad", specialty);
                    row(profile, 1, "Teléfono", phone);
                    hint.setText("Las materias que puede impartir se asignan después en la sección Profesores.");
                    profile.add(hint, 0, 2, 2, 1);
                }
                case Student -> {
                    row(profile, 0, "Carrera", career);
                    row(profile, 1, "Semestre", semester);
                    hint.setText("Alta directa (p. ej. traslado). Los aspirantes nuevos se aceptan en Admisiones. "
                            + "El grupo se asigna en la sección Alumnos.");
                    profile.add(hint, 0, 2, 2, 1);
                }
                default -> { }
            }
            if (profile.getScene() != null && profile.getScene().getWindow() != null) {
                profile.getScene().getWindow().sizeToScene();
            }
        });
        role.setValue(Role.Teacher);

        AtomicInteger createdId = new AtomicInteger();
        boolean saved = form.showAndSave(() -> {
            if (role.getValue() == null) {
                throw new ServiceException("Selecciona el rol.");
            }
            ProfileData p = switch (role.getValue()) {
                case Admin -> new ProfileData.Admin(position.getText());
                case Teacher -> new ProfileData.Teacher(specialty.getText(), phone.getText());
                case Student -> {
                    if (career.getValue() == null) {
                        throw new ServiceException("Selecciona la carrera del alumno.");
                    }
                    yield new ProfileData.Student(career.getValue().idCareer(), semester.getValue());
                }
                default -> throw new ServiceException("Rol no válido.");
            };
            var data = fields.read();
            char[] password = fields.readPassword();
            return () -> {
                createdId.set(ctx.users().create(data, password, p, session));
                return null;
            };
        });
        return saved ? Optional.of(createdId.get()) : Optional.empty();
    }

    /** Edición de datos de acceso. El rol no se cambia. Devuelve {@code true} si se guardó. */
    static boolean edit(Node owner, AppContext ctx, UserSession session, UserRow user) {
        FormDialog form = new FormDialog(owner, "Editar usuario", "Guardar");
        Label roleLabel = form.add("Rol", new Label(user.role().label()));
        roleLabel.getStyleClass().add("form-readonly");
        UserFields fields = new UserFields(form);
        fields.fill(user.data());
        if (user.idUser() == session.idUser()) {
            fields.lockStatus("No puedes desactivar tu propia cuenta.");
        }
        fields.addPassword(form, false);
        String where = switch (user.role()) {
            case Student -> "Los datos académicos (carrera, grupo, promedio) se editan en la sección Alumnos.";
            case Teacher -> "La especialidad, el teléfono y las materias se editan en la sección Profesores.";
            case Aspirant -> "El estatus de admisión se cambia en la sección Admisiones.";
            case Admin -> "El rol de una cuenta no se puede cambiar; si hace falta otro rol, crea una cuenta nueva.";
        };
        form.addFull(FormDialog.note(where));

        return form.showAndSave(() -> {
            var data = fields.read();
            char[] password = fields.readPassword();
            return () -> {
                ctx.users().update(user.idUser(), data, password, session);
                return null;
            };
        });
    }

    private static GridPane profileGrid() {
        GridPane g = new GridPane();
        g.setHgap(12);
        g.setVgap(10);
        ColumnConstraints labels = new ColumnConstraints();
        labels.setMinWidth(140);
        ColumnConstraints fields = new ColumnConstraints();
        fields.setHgrow(Priority.ALWAYS);
        g.getColumnConstraints().addAll(labels, fields);
        return g;
    }

    private static void row(GridPane g, int row, String label, Node field) {
        g.add(new Label(label), 0, row);
        g.add(field, 1, row);
        if (field instanceof javafx.scene.control.Control c) {
            c.setMaxWidth(Double.MAX_VALUE);
        }
    }
}
