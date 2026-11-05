package mx.bisuni.desktop.model;

import java.time.LocalDate;

/** Fila de la lista de admisiones. */
public record AspirantRow(
        int idAspirant,
        String username,
        String firstName,
        String lastName,
        String email,
        String curp,
        String careerName,
        LocalDate applicationDate,
        AspirantStatus status) {

    public String folio() {
        return Folio.of(idAspirant);
    }
}
