package mx.bisuni.desktop.model;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/** Bloque horario del catálogo {@code time_slots}. */
public record TimeSlot(int idSlot, int number, LocalTime start, LocalTime end) {

    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

    /** p. ej. "07:00 – 08:00". */
    public String range() {
        return HH_MM.format(start) + " – " + HH_MM.format(end);
    }

    /** p. ej. "de 07:00 a 08:00". */
    public String spoken() {
        return "de " + HH_MM.format(start) + " a " + HH_MM.format(end);
    }
}
