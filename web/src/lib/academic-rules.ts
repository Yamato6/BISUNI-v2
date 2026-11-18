/**
 * Reglas puras del kárdex y la asistencia (sin base de datos, para probarlas directo).
 * Las mismas que usa el escritorio: 20 % de faltas = riesgo; aprobada con final >= 6.
 */

/** Porcentaje de faltas a partir del cual el alumno está en riesgo en una materia. */
export const RISK_PCT = 20;

export function atRisk(absencePct: number | null): boolean {
  return absencePct !== null && absencePct > RISK_PCT;
}

/** Resultado de una materia en el kárdex (columna `result` de v_kardex). */
export type KardexResult = "Passed" | "Failed" | "InProgress";

export const RESULT_LABEL: Record<KardexResult, string> = {
  Passed: "Aprobada",
  Failed: "Reprobada",
  InProgress: "En curso",
};

export interface KardexEntry {
  idPeriod: number;
  periodName: string;
  periodStart: string;
  periodStatus: "Planned" | "Active" | "Closed";
  groupName: string;
  groupSemester: number;
  idSubject: number;
  subjectName: string;
  credits: number;
  subjectSemester: number;
  parcial1: number | null;
  parcial2: number | null;
  parcial3: number | null;
  finalGrade: number | null;
  result: KardexResult;
}

export interface KardexPeriod {
  idPeriod: number;
  name: string;
  status: KardexEntry["periodStatus"];
  groups: string[];
  entries: KardexEntry[];
  /** Promedio de las finales del periodo (solo materias con los 3 parciales); null si no hay. */
  average: number | null;
  creditsPassed: number;
}

export function round2(n: number): number {
  return Math.round(n * 100) / 100;
}

/** Agrupa por periodo (del más antiguo al más reciente) y calcula el promedio y créditos de cada uno. */
export function groupByPeriod(entries: KardexEntry[]): KardexPeriod[] {
  const byPeriod = new Map<number, KardexEntry[]>();
  const sorted = [...entries].sort(
    (a, b) => a.periodStart.localeCompare(b.periodStart) || a.subjectSemester - b.subjectSemester
      || a.subjectName.localeCompare(b.subjectName),
  );
  for (const e of sorted) {
    const list = byPeriod.get(e.idPeriod) ?? [];
    list.push(e);
    byPeriod.set(e.idPeriod, list);
  }
  return [...byPeriod.values()].map((list) => {
    const finals = list.map((e) => e.finalGrade).filter((f): f is number => f !== null);
    return {
      idPeriod: list[0].idPeriod,
      name: list[0].periodName,
      status: list[0].periodStatus,
      groups: [...new Set(list.map((e) => e.groupName))],
      entries: list,
      average: finals.length === 0 ? null : round2(finals.reduce((a, b) => a + b, 0) / finals.length),
      creditsPassed: list.filter((e) => e.result === "Passed").reduce((a, e) => a + e.credits, 0),
    };
  });
}

/**
 * Créditos aprobados: cada materia cuenta una vez aunque se haya cursado más de una vez.
 * Materias adeudadas: reprobadas que no se han vuelto a aprobar.
 */
export function creditSummary(entries: KardexEntry[], careerCredits: number) {
  const passed = new Map<number, number>();
  for (const e of entries) if (e.result === "Passed") passed.set(e.idSubject, e.credits);
  const owed = [
    ...new Set(entries.filter((e) => e.result === "Failed" && !passed.has(e.idSubject)).map((e) => e.subjectName)),
  ];
  const approved = [...passed.values()].reduce((a, b) => a + b, 0);
  return {
    approved,
    total: careerCredits,
    percent: careerCredits === 0 ? 0 : Math.min(100, Math.round((approved / careerCredits) * 100)),
    owed,
  };
}
