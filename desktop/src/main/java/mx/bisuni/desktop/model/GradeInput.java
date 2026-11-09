package mx.bisuni.desktop.model;

import java.math.BigDecimal;

/** Parciales capturados para un alumno; {@code null} = sin capturar. */
public record GradeInput(int idStudent, BigDecimal parcial1, BigDecimal parcial2, BigDecimal parcial3) {
}
