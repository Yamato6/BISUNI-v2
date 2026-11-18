import { describe, expect, it } from "vitest";
import { atRisk, creditSummary, groupByPeriod, type KardexEntry } from "./academic-rules";

function entry(over: Partial<KardexEntry>): KardexEntry {
  return {
    idPeriod: 1, periodName: "2026-1", periodStart: "2026-01-12", periodStatus: "Closed", groupName: "IT-1A",
    groupSemester: 1, idSubject: 1, subjectName: "A", credits: 4, subjectSemester: 1,
    parcial1: null, parcial2: null, parcial3: null, finalGrade: null, result: "InProgress", ...over,
  };
}

describe("reglas del kárdex y la asistencia", () => {
  it("riesgo por faltas: más del 20 %", () => {
    expect(atRisk(20)).toBe(false);
    expect(atRisk(20.1)).toBe(true);
    expect(atRisk(null)).toBe(false);
  });

  it("agrupa por periodo con promedio y créditos aprobados", () => {
    const periods = groupByPeriod([
      entry({ idPeriod: 2, periodName: "2026-2", periodStart: "2026-08-17", periodStatus: "Active", idSubject: 3, subjectName: "C" }),
      entry({ idSubject: 2, subjectName: "B", finalGrade: 5, result: "Failed" }),
      entry({ idSubject: 1, subjectName: "A", finalGrade: 9, result: "Passed" }),
    ]);
    expect(periods.map((p) => p.name)).toEqual(["2026-1", "2026-2"]);
    expect(periods[0].entries.map((e) => e.subjectName)).toEqual(["A", "B"]);
    expect(periods[0].average).toBe(7);
    expect(periods[0].creditsPassed).toBe(4);
    expect(periods[1].average).toBeNull();
  });

  it("los créditos cuentan una vez por materia y lo reprobado y luego aprobado no se adeuda", () => {
    const entries = [
      entry({ idSubject: 1, subjectName: "A", credits: 4, result: "Passed" }),
      entry({ idSubject: 2, subjectName: "B", credits: 5, result: "Failed" }),
      entry({ idPeriod: 2, idSubject: 2, subjectName: "B", credits: 5, result: "Passed" }),
      entry({ idSubject: 3, subjectName: "C", credits: 3, result: "Failed" }),
    ];
    expect(creditSummary(entries, 36)).toEqual({ approved: 9, total: 36, percent: 25, owed: ["C"] });
    expect(creditSummary([], 0).percent).toBe(0);
  });
});
