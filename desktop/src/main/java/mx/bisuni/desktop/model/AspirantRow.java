package mx.bisuni.desktop.model;

import java.time.LocalDate;

/** Fila de la lista de admisiones. {@code periodName} es la convocatoria a la que aplicó. */
public record AspirantRow(
        int idAspirant,
        String username,
        String firstName,
        String lastName,
        String email,
        String curp,
        String careerName,
        String periodName,
        LocalDate applicationDate,
        AspirantStatus status) {

    public String folio() {
        return Folio.of(idAspirant);
    }
}
