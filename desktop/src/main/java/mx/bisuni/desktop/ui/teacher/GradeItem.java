package mx.bisuni.desktop.ui.teacher;

import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.binding.StringBinding;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import mx.bisuni.desktop.model.GradeInput;
import mx.bisuni.desktop.model.GradeRow;
import mx.bisuni.desktop.service.GradeService;
import mx.bisuni.desktop.service.ServiceException;

import java.math.BigDecimal;

/** Renglón editable de la hoja de calificaciones: lo leído de la base y lo que se escribe en pantalla. */
public final class GradeItem {

    private final GradeRow row;
    private final StringProperty p1;
    private final StringProperty p2;
    private final StringProperty p3;
    private final BooleanBinding modified;
    private final BooleanBinding invalid;
    private final StringBinding finalPreview;

    public GradeItem(GradeRow row) {
        this.row = row;
        this.p1 = new SimpleStringProperty(format(row.parcial1()));
        this.p2 = new SimpleStringProperty(format(row.parcial2()));
        this.p3 = new SimpleStringProperty(format(row.parcial3()));
        this.modified = Bindings.createBooleanBinding(
                () -> differs(p1.get(), row.parcial1()) || differs(p2.get(), row.parcial2())
                        || differs(p3.get(), row.parcial3()),
                p1, p2, p3);
        this.invalid = Bindings.createBooleanBinding(
                () -> !valid(p1.get()) || !valid(p2.get()) || !valid(p3.get()), p1, p2, p3);
        // Vista previa del final; al guardar lo calcula la base con la misma fórmula.
        this.finalPreview = Bindings.createStringBinding(() -> {
            try {
                BigDecimal f = GradeService.finalGrade(GradeService.parse(p1.get(), ""),
                        GradeService.parse(p2.get(), ""), GradeService.parse(p3.get(), ""));
                return f == null ? "—" : f.toPlainString();
            } catch (ServiceException e) {
                return "—";
            }
        }, p1, p2, p3);
    }

    public GradeRow row() {
        return row;
    }

    public StringProperty parcial(int n) {
        return switch (n) {
            case 1 -> p1;
            case 2 -> p2;
            case 3 -> p3;
            default -> throw new IllegalArgumentException("parcial " + n);
        };
    }

    public BooleanBinding modifiedBinding() {
        return modified;
    }

    public boolean isModified() {
        return modified.get();
    }

    public BooleanBinding invalidBinding() {
        return invalid;
    }

    public StringBinding finalPreview() {
        return finalPreview;
    }

    public void revert() {
        p1.set(format(row.parcial1()));
        p2.set(format(row.parcial2()));
        p3.set(format(row.parcial3()));
    }

    /** Convierte lo capturado; el mensaje de error nombra al alumno y al parcial. */
    public GradeInput toInput() throws ServiceException {
        String who = " de " + row.fullName();
        return new GradeInput(row.idStudent(),
                GradeService.parse(p1.get(), "el parcial 1" + who),
                GradeService.parse(p2.get(), "el parcial 2" + who),
                GradeService.parse(p3.get(), "el parcial 3" + who));
    }

    static String format(BigDecimal v) {
        return v == null ? "" : v.stripTrailingZeros().toPlainString();
    }

    static boolean valid(String text) {
        try {
            GradeService.parse(text, "");
            return true;
        } catch (ServiceException e) {
            return false;
        }
    }

    /** "8", "8.0" y "8,00" son la misma calificación: se compara el número, no el texto. */
    private static boolean differs(String text, BigDecimal original) {
        try {
            BigDecimal v = GradeService.parse(text, "");
            if (v == null || original == null) {
                return v != original;
            }
            return v.compareTo(original) != 0;
        } catch (ServiceException e) {
            return true; // texto inválido cuenta como cambio (y se marca en rojo)
        }
    }
}
