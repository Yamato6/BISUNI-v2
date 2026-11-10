import { describe, expect, it } from "vitest";
import { formatGrade, gradeLevel, gradeStatus, summarize, toNumber } from "./grades";

describe("grades", () => {
  it("convierte NUMERIC de Postgres", () => {
    expect(toNumber("8.17")).toBe(8.17);
    expect(toNumber(null)).toBeNull();
    expect(toNumber("")).toBeNull();
  });

  it("estatus como en la v1: aprobada >= 6, reprobada < 6, en proceso sin final", () => {
    expect(gradeStatus(6)).toBe("aprobada");
    expect(gradeStatus(5.99)).toBe("reprobada");
    expect(gradeStatus(null)).toBe("en-proceso");
  });

  it("nivel visual", () => {
    expect(gradeLevel(8)).toBe("alta");
    expect(gradeLevel(7.5)).toBe("media");
    expect(gradeLevel(6.9)).toBe("baja");
    expect(gradeLevel(null)).toBe("vacia");
  });

  it("resumen: cuenta y promedia solo finales completas", () => {
    expect(summarize([9, 5, null, 8])).toEqual({ total: 4, approved: 2, failed: 1, inProgress: 1, average: 7.33 });
    expect(summarize([null])).toEqual({ total: 1, approved: 0, failed: 0, inProgress: 1, average: null });
    expect(summarize([])).toEqual({ total: 0, approved: 0, failed: 0, inProgress: 0, average: null });
  });

  it("formato con 2 decimales", () => {
    expect(formatGrade(8)).toBe("8.00");
    expect(formatGrade(null)).toBe("—");
  });
});
