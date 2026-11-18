import postgres from "postgres";
import { describe, expect, it } from "vitest";
import { getAnnouncements, getAttendance, getCurrentPeriod, getKardex } from "@/lib/academic";
import { renderBoleta, renderConstancia, renderKardex } from "@/lib/pdf/documents";
import { setOwnPassword } from "@/lib/profile";
import { authorizeStudent } from "@/lib/auth/authorize";
import { getOverview } from "@/lib/student";

async function query<T extends object>(text: string): Promise<T[]> {
  const sql = postgres(process.env.DATABASE_URL!, { max: 1, onnotice: () => {} });
  const rows = await sql.unsafe<T[]>(text);
  await sql.end();
  return rows;
}

/*
 * Seed: student001 (alumno 1) en IT-1A de 2026-2. La generación 2026-1 (alumnos 53-62) cursó
 * IT-1A en 2026-1 y hoy está en IT-2A; el 55 (student053) falta mucho y el 58 (student056)
 * reprobó Discrete Mathematics. Hay un aviso general, uno para IT, uno para IT-1A y uno vencido.
 */
describe("avisos del alumno", () => {
  it("generales, de su carrera y de su grupo; nunca los vencidos", async () => {
    const it1a = await getAnnouncements(1);
    expect(it1a.map((a) => a.scope)).toEqual(["Para tu grupo", "Para tu carrera", "Para toda la escuela"]);
    expect(it1a.some((a) => a.title === "Aviso vencido")).toBe(false);
    expect((await getAnnouncements(21)).map((a) => a.scope)).toEqual(["Para toda la escuela"]); // BA-1A
  });
});

describe("asistencia", () => {
  it("resumen por materia con faltas y riesgo", async () => {
    const { groupName, subjects } = await getAttendance(55);
    expect(groupName).toBe("IT-2A");
    expect(subjects.map((s) => [s.subjectName, s.absencePct, s.atRisk])).toEqual([
      ["Database Basics", 35.4, true],
      ["Web Development I", 35.4, true],
    ]);
    expect(subjects[0].incidents.filter((i) => i.status === "Absent")).toHaveLength(17);
    const s1 = await getAttendance(1);
    expect(s1.subjects.every((s) => !s.atRisk && s.sessions > 0)).toBe(true);
    expect((await getAttendance(51)).subjects).toEqual([]); // sin grupo
  });
});

describe("kárdex", () => {
  it("historial por periodo, créditos y adeudos", async () => {
    const k = (await getKardex(58))!;
    expect(k.periods.map((p) => [p.name, p.groups, p.status])).toEqual([
      ["2026-1", ["IT-1A"], "Closed"],
      ["2026-2", ["IT-2A"], "Active"],
    ]);
    expect(k.periods[0].entries.map((e) => [e.subjectName, e.finalGrade, e.result])).toEqual([
      ["Discrete Mathematics", 4.83, "Failed"],
      ["Programming Fundamentals", 7, "Passed"],
    ]);
    expect(k.periods[1].entries.every((e) => e.result === "InProgress")).toBe(true);
    // IT suma 32 créditos; aprobó Programming Fundamentals (4).
    expect(k.credits).toEqual({ approved: 4, total: 32, percent: 13, owed: ["Discrete Mathematics"] });
    expect(k.gpa).toBe(5.92);

    const fresh = (await getKardex(51))!;
    expect(fresh.periods).toEqual([]);
    expect(fresh.gpa).toBeNull();
  });
});

describe("documentos PDF", () => {
  it("constancia, kárdex y boleta son PDF válidos", async () => {
    const me = (await getOverview(53))!;
    const period = await getCurrentPeriod(53);
    expect(period).toEqual({ name: "2026-2", startDate: "2026-08-17", endDate: "2026-12-18" });
    const kardex = (await getKardex(53))!;
    for (const pdf of [
      await renderConstancia(me, period),
      await renderKardex(me, kardex),
      await renderBoleta(me, kardex.periods[0]),
    ]) {
      expect(pdf.subarray(0, 5).toString()).toBe("%PDF-");
      expect(pdf.length).toBeGreaterThan(2000);
    }
  });
});

describe("contraseña temporal", () => {
  it("solo con la marca de restablecimiento; la quita y queda en la bitácora", async () => {
    expect(await setOwnPassword(13, "otra-clave-1")).toMatchObject({ ok: false });

    await query("UPDATE users SET must_change_password = true WHERE id_user = 13");
    expect(await setOwnPassword(13, "password123")).toEqual({
      ok: false,
      message: "La nueva contraseña debe ser distinta de la temporal.",
    });
    expect(await setOwnPassword(13, "mi-clave-nueva")).toEqual({ ok: true });
    expect((await authorizeStudent({ email: "student001@bisuni.edu", password: "mi-clave-nueva" })).ok).toBe(true);
    const [row] = await query<{ must: boolean }>("SELECT must_change_password AS must FROM users WHERE id_user = 13");
    expect(row.must).toBe(false);
    const audit = await query<{ actor: number }>(
      "SELECT actor_user AS actor FROM audit_log WHERE entity = 'users' AND entity_id = '13' ORDER BY id_audit DESC LIMIT 1",
    );
    expect(audit[0].actor).toBe(13);
  });
});
