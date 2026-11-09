package mx.bisuni.desktop.ui.schedule;

import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import mx.bisuni.desktop.AppContext;
import mx.bisuni.desktop.model.Catalog;
import mx.bisuni.desktop.model.GroupSubjectRow;
import mx.bisuni.desktop.model.ScheduleEntry;
import mx.bisuni.desktop.model.TimeSlot;
import mx.bisuni.desktop.model.Weekday;
import mx.bisuni.desktop.session.UserSession;
import mx.bisuni.desktop.ui.Async;
import mx.bisuni.desktop.ui.Dialogs;
import mx.bisuni.desktop.ui.Option;
import mx.bisuni.desktop.ui.ScheduleGrid;
import mx.bisuni.desktop.ui.shell.ShellController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;

/**
 * Horarios por grupo (editable) y por profesor (solo consulta: se deriva de los grupos).
 */
public class ScheduleController implements ShellController.ContentView {

    /** Catálogos de la vista. */
    private record Catalogs(List<TimeSlot> slots, List<Catalog.Group> groups, List<Catalog.Career> careers,
                            List<Catalog.Teacher> teachers) {
    }

    /** Datos del grupo elegido: su horario y sus materias. */
    private record GroupData(List<ScheduleEntry> entries, List<GroupSubjectRow> subjects) {
    }

    @FXML private VBox root;
    @FXML private ToggleGroup modeGroup;
    @FXML private ToggleButton groupMode;
    @FXML private ToggleButton teacherMode;
    @FXML private Label subtitleLabel;
    @FXML private Label selectorLabel;
    @FXML private ComboBox<Option<Integer>> selector;
    @FXML private Label totalLabel;
    @FXML private ScheduleGrid grid;
    @FXML private Label sideTitle;
    @FXML private VBox sideList;
    @FXML private Label hintLabel;

    private AppContext ctx;
    private UserSession session;
    private Catalogs catalogs;
    private List<GroupSubjectRow> groupSubjects = List.of();
    private int loadSeq;
    private boolean switchingMode;

    @FXML
    private void initialize() {
        // Siempre debe haber un modo elegido.
        modeGroup.selectedToggleProperty().addListener((obs, old, sel) -> {
            if (sel == null && old != null) {
                old.setSelected(true);
            }
        });
        grid.setOnCellClicked(this::onCellClicked);
    }

    public void init(AppContext ctx, UserSession session) {
        this.ctx = ctx;
        this.session = session;
        Async.run(() -> new Catalogs(ctx.schedule().timeSlots(), ctx.catalog().groups(), ctx.catalog().careers(),
                        ctx.catalog().teachers()),
                c -> {
                    catalogs = c;
                    grid.setSlots(c.slots());
                    modeGroup.selectedToggleProperty().addListener((o, a, b) -> {
                        if (b != null) {
                            applyMode();
                        }
                    });
                    selector.valueProperty().addListener((o, a, b) -> {
                        if (!switchingMode) {
                            load();
                        }
                    });
                    applyMode();
                },
                error -> Dialogs.error(root, error));
    }

    @Override
    public Node root() {
        return root;
    }

    private boolean isGroupMode() {
        return groupMode.isSelected();
    }

    /** Cambia entre horario de grupo y de profesor. */
    private void applyMode() {
        switchingMode = true;
        List<Option<Integer>> options = new ArrayList<>();
        if (isGroupMode()) {
            selectorLabel.setText("Grupo:");
            subtitleLabel.setText("Haz clic en un bloque para poner o quitar una clase. El profesor es el asignado "
                    + "a la materia en el grupo; si ya tiene clase a esa hora en otro grupo, no se permite.");
            Map<Integer, String> careerNames = new LinkedHashMap<>();
            catalogs.careers().forEach(c -> careerNames.put(c.idCareer(), c.name()));
            for (Catalog.Group g : catalogs.groups()) {
                options.add(new Option<>(g.name() + " — " + careerNames.getOrDefault(g.idCareer(), "")
                        + ", " + g.semester() + "° sem.", g.idGroup()));
            }
            grid.setSecondLine(ScheduleEntry::teacherName);
        } else {
            selectorLabel.setText("Profesor:");
            subtitleLabel.setText("El horario del profesor se arma solo a partir de los horarios de grupo; "
                    + "para cambiarlo, edita el horario del grupo correspondiente.");
            for (Catalog.Teacher t : catalogs.teachers()) {
                options.add(new Option<>(t.name(), t.idTeacher()));
            }
            grid.setSecondLine(ScheduleEntry::groupName);
        }
        grid.setEditable(isGroupMode());
        selector.getItems().setAll(options);
        selector.getSelectionModel().selectFirst();
        switchingMode = false;
        load();
    }

    private void load() {
        Option<Integer> sel = selector.getValue();
        int seq = ++loadSeq;
        if (sel == null) {
            grid.setEntries(List.of());
            renderSide(List.of(), List.of());
            return;
        }
        int id = sel.value();
        if (isGroupMode()) {
            Async.run(() -> new GroupData(ctx.schedule().forGroup(id), ctx.groups().subjects(id)),
                    d -> {
                        if (seq != loadSeq) {
                            return;
                        }
                        groupSubjects = d.subjects();
                        grid.setEntries(d.entries());
                        renderSide(d.entries(), d.subjects());
                    },
                    error -> Dialogs.error(root, error));
        } else {
            Async.run(() -> ctx.schedule().forTeacher(id),
                    entries -> {
                        if (seq != loadSeq) {
                            return;
                        }
                        groupSubjects = List.of();
                        grid.setEntries(entries);
                        renderSide(entries, List.of());
                    },
                    error -> Dialogs.error(root, error));
        }
    }

    /** Panel lateral: materias del grupo con horas programadas, o resumen del profesor. */
    private void renderSide(List<ScheduleEntry> entries, List<GroupSubjectRow> subjects) {
        sideList.getChildren().clear();
        totalLabel.setText(entries.size() + (entries.size() == 1 ? " clase" : " clases") + " por semana");
        if (isGroupMode()) {
            sideTitle.setText("Materias del grupo");
            if (subjects.isEmpty()) {
                sideList.getChildren().add(note("El grupo no tiene materias. Agrégalas en Grupos → Materias y alumnos."));
            }
            for (GroupSubjectRow s : subjects) {
                long scheduled = entries.stream().filter(e -> e.idSubject() == s.idSubject()).count();
                String detail = s.hasTeacher() ? s.teacherName() : "Sin profesor: no se puede agendar";
                String hours = scheduled + " de " + s.weeklyHours() + " h";
                Label h = new Label(hours);
                h.getStyleClass().add(scheduled == s.weeklyHours() ? "hours-ok"
                        : scheduled > s.weeklyHours() ? "hours-over" : "hours-under");
                sideList.getChildren().add(chip(s.idSubject(), s.subjectName(), detail, h, !s.hasTeacher()));
            }
            hintLabel.setText("Verde: horas completas. Ámbar: faltan horas. Rojo: más horas de las indicadas en la materia.");
        } else {
            sideTitle.setText("Clases por grupo y materia");
            Map<String, long[]> counts = new LinkedHashMap<>();
            Map<String, Integer> subjectIds = new LinkedHashMap<>();
            for (ScheduleEntry e : entries) {
                String key = e.groupName() + "|" + e.subjectName();
                counts.computeIfAbsent(key, k -> new long[1])[0]++;
                subjectIds.put(key, e.idSubject());
            }
            if (counts.isEmpty()) {
                sideList.getChildren().add(note("El profesor no tiene clases en ningún horario de grupo."));
            }
            counts.forEach((key, n) -> {
                String[] parts = key.split("\\|", 2);
                Label h = new Label(n[0] + " h");
                sideList.getChildren().add(chip(subjectIds.get(key), parts[1], parts[0], h, false));
            });
            hintLabel.setText("Solo consulta. Para cambiar una clase, edita el horario del grupo.");
        }
    }

    private static Node chip(int idSubject, String title, String detail, Label right, boolean warn) {
        Region color = new Region();
        color.setMinSize(12, 12);
        color.setMaxSize(12, 12);
        color.setStyle("-fx-background-color: " + ScheduleGrid.colorFor(idSubject)
                + "; -fx-background-radius: 3; -fx-border-color: #B8C4BA; -fx-border-radius: 3;");
        Label t = new Label(title);
        t.setWrapText(true);
        t.getStyleClass().add("schedule-side-title");
        Label d = new Label(detail);
        d.setWrapText(true);
        d.getStyleClass().add(warn ? "hours-over" : "form-note");
        VBox text = new VBox(1, t, d);
        HBox.setHgrow(text, Priority.ALWAYS);
        right.setMinWidth(Region.USE_PREF_SIZE); // las horas nunca se recortan; se ajusta el nombre
        HBox row = new HBox(8, color, text, right);
        row.setAlignment(Pos.TOP_LEFT);
        return row;
    }

    private static Label note(String text) {
        Label l = new Label(text);
        l.setWrapText(true);
        l.getStyleClass().add("form-note");
        return l;
    }

    // ---------------- Edición (solo horario de grupo) ----------------

    private void onCellClicked(Weekday day, TimeSlot slot, Optional<ScheduleEntry> current, Node cell) {
        Option<Integer> sel = selector.getValue();
        if (!isGroupMode() || sel == null) {
            return;
        }
        int idGroup = sel.value();
        ContextMenu menu = new ContextMenu();
        MenuItem header = new MenuItem(day.label() + " " + slot.range());
        header.setDisable(true);
        menu.getItems().addAll(header, new SeparatorMenuItem());
        if (groupSubjects.isEmpty()) {
            MenuItem none = new MenuItem("El grupo no tiene materias");
            none.setDisable(true);
            menu.getItems().add(none);
        }
        for (GroupSubjectRow s : groupSubjects) {
            boolean isCurrent = current.isPresent() && current.get().idSubject() == s.idSubject();
            String text = (isCurrent ? "✓ " : "") + s.subjectName()
                    + (s.hasTeacher() ? " — " + s.teacherName() : " (sin profesor)");
            MenuItem item = new MenuItem(text);
            item.setDisable(!s.hasTeacher() || isCurrent);
            item.setOnAction(e -> run(() -> {
                ctx.schedule().assign(idGroup, day, slot, s.idSubject(), session);
                return null;
            }));
            menu.getItems().add(item);
        }
        if (current.isPresent()) {
            MenuItem clear = new MenuItem("Vaciar bloque");
            clear.setOnAction(e -> run(() -> {
                ctx.schedule().clear(idGroup, day, slot, session);
                return null;
            }));
            menu.getItems().addAll(new SeparatorMenuItem(), clear);
        }
        menu.show(cell, Side.BOTTOM, 0, 0);
    }

    private void run(Callable<?> work) {
        grid.setDisable(true);
        Async.run(work,
                r -> {
                    grid.setDisable(false);
                    load();
                },
                error -> {
                    grid.setDisable(false);
                    Dialogs.error(root, error);
                    load();
                });
    }

    /** Para la prueba de humo: número de clases que muestra la cuadrícula. */
    public int shownEntries() {
        return grid.entryCount();
    }
}
