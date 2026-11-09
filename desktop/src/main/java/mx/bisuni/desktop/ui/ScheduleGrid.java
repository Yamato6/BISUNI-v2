package mx.bisuni.desktop.ui;

import javafx.geometry.HPos;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.RowConstraints;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import mx.bisuni.desktop.model.ScheduleEntry;
import mx.bisuni.desktop.model.TimeSlot;
import mx.bisuni.desktop.model.Weekday;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * Cuadrícula de horario: lunes a viernes por los bloques de {@code time_slots}. Se usa para el
 * horario de grupo (editable), el de profesor y, más adelante, "Mi horario" del profesor.
 */
public final class ScheduleGrid extends GridPane {

    /** Recibe el clic en un bloque; {@code entry} está vacío si el bloque no tiene clase. */
    @FunctionalInterface
    public interface CellHandler {
        void onClick(Weekday day, TimeSlot slot, Optional<ScheduleEntry> entry, Node cell);
    }

    /** Colores suaves para distinguir materias; el mismo id de materia siempre usa el mismo color. */
    private static final String[] PALETTE = {
            "#DCEBDC", "#DCE8F5", "#F5E6D3", "#EBDCF0", "#F5F0C8",
            "#D3F0EE", "#F5DCDC", "#E3E3F7", "#E6F0D0", "#F0E0E8"};

    private final Map<Weekday, Map<Integer, StackPane>> cells = new EnumMap<>(Weekday.class);
    private final Map<String, ScheduleEntry> entries = new HashMap<>();
    private List<TimeSlot> slots = List.of();
    private Function<ScheduleEntry, String> secondLine = ScheduleEntry::teacherName;
    private CellHandler handler;
    private boolean editable;

    public ScheduleGrid() {
        getStyleClass().add("schedule-grid");
        setHgap(4);
        setVgap(4);
    }

    public static String colorFor(int idSubject) {
        return PALETTE[Math.floorMod(idSubject * 7, PALETTE.length)];
    }

    /** Arma la cuadrícula con los bloques del catálogo. */
    public void setSlots(List<TimeSlot> slots) {
        this.slots = List.copyOf(slots);
        getChildren().clear();
        getColumnConstraints().clear();
        getRowConstraints().clear();
        cells.clear();

        ColumnConstraints timeCol = new ColumnConstraints(110);
        getColumnConstraints().add(timeCol);
        for (int i = 0; i < Weekday.values().length; i++) {
            ColumnConstraints c = new ColumnConstraints();
            c.setHgrow(Priority.ALWAYS);
            c.setPercentWidth(-1);
            c.setMinWidth(110);
            c.setFillWidth(true);
            getColumnConstraints().add(c);
        }
        getRowConstraints().add(new RowConstraints(30));
        for (int i = 0; i < slots.size(); i++) {
            RowConstraints r = new RowConstraints();
            r.setMinHeight(48);
            r.setVgrow(Priority.ALWAYS);
            r.setFillHeight(true);
            getRowConstraints().add(r);
        }

        for (Weekday d : Weekday.values()) {
            Label h = new Label(d.label());
            h.getStyleClass().add("schedule-day");
            h.setMaxWidth(Double.MAX_VALUE);
            add(h, d.ordinal() + 1, 0);
            GridPane.setHalignment(h, HPos.CENTER);
            cells.put(d, new HashMap<>());
        }
        for (int r = 0; r < slots.size(); r++) {
            TimeSlot slot = slots.get(r);
            Label t = new Label(slot.range());
            t.getStyleClass().add("schedule-time");
            add(t, 0, r + 1);
            GridPane.setValignment(t, VPos.CENTER);
            for (Weekday d : Weekday.values()) {
                StackPane cell = new StackPane();
                cell.getStyleClass().add("schedule-cell");
                cell.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
                cell.setOnMouseClicked(e -> {
                    if (handler != null && editable) {
                        handler.onClick(d, slot, Optional.ofNullable(entries.get(key(d, slot.idSlot()))), cell);
                    }
                });
                cells.get(d).put(slot.idSlot(), cell);
                add(cell, d.ordinal() + 1, r + 1);
            }
        }
        render();
    }

    /** Segunda línea de cada clase: profesor (horario de grupo) o grupo (horario de profesor). */
    public void setSecondLine(Function<ScheduleEntry, String> secondLine) {
        this.secondLine = secondLine;
        render();
    }

    public void setEntries(List<ScheduleEntry> list) {
        entries.clear();
        for (ScheduleEntry e : list) {
            entries.put(key(e.day(), e.idSlot()), e);
        }
        render();
    }

    public void setEditable(boolean editable) {
        this.editable = editable;
        render();
    }

    public void setOnCellClicked(CellHandler handler) {
        this.handler = handler;
    }

    public int entryCount() {
        return entries.size();
    }

    private void render() {
        for (var byDay : cells.entrySet()) {
            for (var bySlot : byDay.getValue().entrySet()) {
                StackPane cell = bySlot.getValue();
                ScheduleEntry e = entries.get(key(byDay.getKey(), bySlot.getKey()));
                cell.getChildren().clear();
                cell.getStyleClass().removeAll("schedule-cell-filled", "schedule-cell-editable");
                cell.setStyle(null);
                Tooltip.uninstall(cell, null);
                if (editable) {
                    cell.getStyleClass().add("schedule-cell-editable");
                    cell.setCursor(Cursor.HAND);
                } else {
                    cell.setCursor(Cursor.DEFAULT);
                }
                if (e == null) {
                    if (editable) {
                        Label plus = new Label("+");
                        plus.getStyleClass().add("schedule-empty");
                        cell.getChildren().add(plus);
                    }
                    continue;
                }
                Label subject = new Label(e.subjectName());
                subject.getStyleClass().add("schedule-subject");
                subject.setWrapText(true);
                Label second = new Label(secondLine.apply(e));
                second.getStyleClass().add("schedule-second");
                VBox box = new VBox(2, subject, second);
                box.setAlignment(Pos.CENTER);
                box.setFillWidth(true);
                cell.getChildren().add(box);
                cell.getStyleClass().add("schedule-cell-filled");
                cell.setStyle("-fx-background-color: " + colorFor(e.idSubject()) + ";");
                Tooltip.install(cell, new Tooltip(e.subjectName() + "\n" + e.teacherName() + " · " + e.groupName()));
            }
        }
    }

    private static String key(Weekday d, int idSlot) {
        return d.name() + ":" + idSlot;
    }

    public List<TimeSlot> slots() {
        return slots;
    }
}
