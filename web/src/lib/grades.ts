/**
 * Reglas de calificaciones del portal (las mismas que usaba la v1):
 * - Aprobada: calificación final >= 6.
 * - Reprobada: calificación final < 6.
 * - En proceso: falta algún parcial (la base deja final_grade en NULL).
 * La calificación final la calcula PostgreSQL (columna generada); aquí solo se lee.
 */
export const PASSING_GRADE = 6;

export type GradeStatus = "aprobada" | "reprobada" | "en-proceso";

/** Postgres devuelve NUMERIC como texto ("8.17"); null si no hay valor. */
export function toNumber(value: string | null | undefined): number | null {
  if (value === null || value === undefined || value === "") return null;
  const n = Number(value);
  return Number.isFinite(n) ? n : null;
}

export function gradeStatus(finalGrade: number | null): GradeStatus {
  if (finalGrade === null) return "en-proceso";
  return finalGrade >= PASSING_GRADE ? "aprobada" : "reprobada";
}

export const STATUS_LABEL: Record<GradeStatus, string> = {
  aprobada: "Aprobada",
  reprobada: "Reprobada",
  "en-proceso": "En proceso",
};

/** Nivel visual de una calificación (alta >= 8, media >= 7, baja < 7), como en la v1. */
export function gradeLevel(value: number | null): "alta" | "media" | "baja" | "vacia" {
  if (value === null) return "vacia";
  if (value >= 8) return "alta";
  if (value >= 7) return "media";
  return "baja";
}

export interface GradeSummary {
  total: number;
  approved: number;
  failed: number;
  inProgress: number;
  /** Promedio de las calificaciones finales ya completas, a 2 decimales; null si no hay ninguna. */
  average: number | null;
}

export function summarize(finals: Array<number | null>): GradeSummary {
  const complete = finals.filter((f): f is number => f !== null);
  const approved = complete.filter((f) => f >= PASSING_GRADE).length;
  const average =
    complete.length === 0
      ? null
      : Math.round((complete.reduce((a, b) => a + b, 0) / complete.length) * 100) / 100;
  return {
    total: finals.length,
    approved,
    failed: complete.length - approved,
    inProgress: finals.length - complete.length,
    average,
  };
}

/** Siempre con 2 decimales, como las guarda la base (NUMERIC(4,2)); "—" si no hay valor. */
export function formatGrade(value: number | null): string {
  return value === null ? "—" : value.toFixed(2);
}
