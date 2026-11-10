import postgres from "postgres";
import { describe, expect, it } from "vitest";
import { getGrades, getOverview, getSchedule } from "@/lib/student";

async function exec(query: string) {
  const sql = postgres(process.env.DATABASE_URL!, { max: 1, onnotice: () => {} });
  await sql.unsafe(query);
  await sql.end();
}

/**
 * Seed: el alumno 1 (student001) está en IT-1A (grupo 1), cuyo tutor es Juan Pérez. IT-1A cursa
 * Programming Fundamentals (Juan Pérez) y Discrete Mathematics (María García), con 15 clases
 * semanales cada una. Sus parciales en ambas son 8.0, 8.0 y 8.5 → final 8.17.
 * El alumno 51 viene de admisión: sin grupo y con registro de inscripción.
 */
describe("datos del alumno", () => {
  it("resumen general", async () => {
    expect(await getOverview(1)).toEqual({
      firstName: "Carlos",
      lastName: "Hernández",
      email: "student001@bisuni.edu",
      username: "student001",
      careerName: "Information Technology",
      semester: 1,
      gpa: 8,
      groupName: "IT-1A",
      tutorName: "Juan Pérez",
      enrollmentDate: null,
    });
    const admitted = await getOverview(51);
    expect(admitted?.groupName).toBeNull();
    expect(admitted?.enrollmentDate).toBe("2025-09-01");
    expect(await getOverview(9999)).toBeNull();
  });

  it("calificaciones del grupo actual con su profesor y final calculada por la base", async () => {
    const { subjects, summary } = await getGrades(1);
    expect(subjects.map((s) => [s.subjectName, s.teacherName, s.finalGrade])).toEqual([
      ["Discrete Mathematics", "María García", 8.17],
      ["Programming Fundamentals", "Juan Pérez", 8.17],
    ]);
    expect(subjects.every((s) => s.current)).toBe(true);
    expect(summary).toEqual({ total: 2, approved: 2, failed: 0, inProgress: 0, average: 8.17 });
  });

  it("una materia del grupo sin calificaciones aparece en proceso", async () => {
    await exec("INSERT INTO teacher_subject (id_teacher, id_subject) VALUES (1, 3) ON CONFLICT DO NOTHING");
    await exec("INSERT INTO group_subject (id_group, id_subject, id_teacher) VALUES (1, 3, 1)");
    const { subjects, summary } = await getGrades(1);
    const db = subjects.find((s) => s.idSubject === 3);
    expect(db?.finalGrade).toBeNull();
    expect(db?.parcial1).toBeNull();
    expect(summary.inProgress).toBe(1);
  });

  it("si lo cambian de grupo conserva las calificaciones del grupo anterior", async () => {
    await exec("UPDATE student_profile SET id_group = 2 WHERE id_student = 1");
    const { subjects } = await getGrades(1);
    const previous = subjects.filter((s) => s.groupName === "IT-1A");
    const current = subjects.filter((s) => s.groupName === "IT-1B");
    expect(previous).toHaveLength(2);
    expect(previous.every((s) => !s.current && s.finalGrade === 8.17)).toBe(true);
    expect(current.length).toBeGreaterThan(0);
    expect(current.every((s) => s.current)).toBe(true);
    expect(subjects[0].current).toBe(true); // el grupo actual va primero
  });

  it("alumno sin grupo ni calificaciones", async () => {
    const { subjects, summary } = await getGrades(51);
    expect(subjects).toEqual([]);
    expect(summary.average).toBeNull();
    const schedule = await getSchedule(51);
    expect(schedule.groupName).toBeNull();
    expect(schedule.classes).toEqual([]);
    expect(schedule.slots).toHaveLength(9);
  });

  it("horario del grupo con profesor", async () => {
    const s = await getSchedule(11); // alumno 11 → IT-1B
    expect(s.groupName).toBe("IT-1B");
    expect(s.classes).toHaveLength(30);
    expect(new Set(s.classes.map((c) => c.teacherName))).toEqual(new Set(["Juan Pérez", "María García"]));
  });
});
