package mx.bisuni.desktop.ui;

import javafx.scene.control.DatePicker;
import javafx.util.StringConverter;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/** Selectores de fecha en formato mexicano (dd/MM/aaaa) en lugar del de la configuración regional. */
public final class DatePickers {

    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private DatePickers() {
    }

    public static void spanish(DatePicker... pickers) {
        for (DatePicker p : pickers) {
            p.setPromptText(p.getPromptText() == null || p.getPromptText().isBlank() ? "dd/mm/aaaa" : p.getPromptText());
            p.setConverter(new StringConverter<>() {
                @Override
                public String toString(LocalDate date) {
                    return date == null ? "" : FORMAT.format(date);
                }

                @Override
                public LocalDate fromString(String text) {
                    if (text == null || text.isBlank()) {
                        return null;
                    }
                    try {
                        return LocalDate.parse(text.strip(), FORMAT);
                    } catch (DateTimeParseException e) {
                        return p.getValue(); // texto inválido: conserva la fecha anterior
                    }
                }
            });
        }
    }
}
