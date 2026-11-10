package mx.bisuni.desktop.model;

import java.time.LocalDate;

/**
 * Periodo escolar (ciclo). Solo uno puede estar activo.
 *
 * @param admissionsOpen la web acepta registros de aspirantes para este periodo
 * @param groupCount     grupos del periodo
 */
public record Period(int idPeriod, String name, LocalDate startDate, LocalDate endDate, PeriodStatus status,
                     boolean admissionsOpen, int groupCount) {

    public boolean isClosed() {
        return status == PeriodStatus.Closed;
    }

    public PeriodData data() {
        return new PeriodData(name, startDate, endDate);
    }

    @Override
    public String toString() {
        return name + (status == PeriodStatus.Active ? " (activo)" : status == PeriodStatus.Closed ? " (cerrado)" : " (planeado)");
    }
}
