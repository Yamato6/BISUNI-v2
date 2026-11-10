package mx.bisuni.desktop.model;

import java.time.LocalDate;

/** Datos editables de un periodo escolar. */
public record PeriodData(String name, LocalDate startDate, LocalDate endDate) {
}
