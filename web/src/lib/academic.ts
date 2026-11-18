import "server-only";
import { and, asc, desc, eq, gt, inArray, isNull, or, sql } from "drizzle-orm";
import { alias } from "drizzle-orm/pg-core";
import { db, schema } from "@/db";
import { atRisk, creditSummary, groupByPeriod, type KardexEntry, type KardexResult } from "@/lib/academic-rules";
import { toNumber } from "@/lib/grades";

const s = schema;
const teacherUser = alias(s.users, "teacher_user");

async function studentContext(idStudent: number) {
  const rows = await db()
    .select({ idCareer: s.studentProfile.idCareer, idGroup: s.studentProfile.idGroup })
    .from(s.studentProfile)
    .where(eq(s.studentProfile.idStudent, idStudent))
    .limit(1);
  return rows[0] ?? null;
}

// ---------------------------------------------------------------------------
// Avisos
// ---------------------------------------------------------------------------

export interface Announcement {
  id: number;
  title: string;
  body: string;
  publishedAt: string;
  authorName: string;
  /** "Para toda la escuela", "Para tu carrera" o "Para tu grupo". */
  scope: string;
}

/** Avisos vigentes para el alumno: los generales, los de su carrera y los de su grupo actual. */
export async function getAnnouncements(idStudent: number, limit = 10): Promise<Announcement[]> {
  const ctx = await studentContext(idStudent);
  if (!ctx) return [];
  const audience = [
    eq(s.announcement.audience, "All"),
    and(eq(s.announcement.audience, "Career"), eq(s.announcement.idCareer, ctx.idCareer)),
  ];
  if (ctx.idGroup !== null) {
    audience.push(and(eq(s.announcement.audience, "Group"), eq(s.announcement.idGroup, ctx.idGroup)));
  }
  const rows = await db()
    .select({
      id: s.announcement.idAnnouncement,
      title: s.announcement.title,
      body: s.announcement.body,
      audience: s.announcement.audience,
      publishedAt: s.announcement.publishedAt,
      first: s.users.firstName,
      last: s.users.lastName,
    })
    .from(s.announcement)
    .innerJoin(s.users, eq(s.users.idUser, s.announcement.authorUser))
    .where(and(or(...audience), or(isNull(s.announcement.expiresAt), gt(s.announcement.expiresAt, sql`now()`))))
    .orderBy(desc(s.announcement.publishedAt), desc(s.announcement.idAnnouncement))
    .limit(limit);
  const scope = { All: "Para toda la escuela", Career: "Para tu carrera", Group: "Para tu grupo" } as const;
  return rows.map((r) => ({
    id: r.id,
    title: r.title,
    body: r.body,
    publishedAt: r.publishedAt,
    authorName: `${r.first} ${r.last}`,
    scope: scope[r.audience],
  }));
}

// ---------------------------------------------------------------------------
// Asistencia
// ---------------------------------------------------------------------------

export interface SubjectAttendance {
  idSubject: number;
  subjectName: string;
  teacherName: string | null;
  sessions: number;
  absences: number;
  lates: number;
  excused: number;
  absencePct: number | null;
  atRisk: boolean;
  /** Faltas, retardos y justificadas, de la más reciente a la más antigua. */
  incidents: Array<{ date: string; status: "Absent" | "Late" | "Excused" }>;
}

/** Asistencia del alumno en las materias de su grupo actual. */
export async function getAttendance(idStudent: number): Promise<{ groupName: string | null; subjects: SubjectAttendance[] }> {
  const ctx = await studentContext(idStudent);
  if (!ctx?.idGroup) return { groupName: null, subjects: [] };
  const idGroup = ctx.idGroup;

  const rows = await db()
    .select({
      idSubject: s.subjects.idSubject,
      subjectName: s.subjects.subjectName,
      groupName: s.schoolGroups.groupName,
      teacherFirst: teacherUser.firstName,
      teacherLast: teacherUser.lastName,
      sessions: s.vAttendanceSummary.sessions,
      absences: s.vAttendanceSummary.absences,
      lates: s.vAttendanceSummary.lates,
      excused: s.vAttendanceSummary.excused,
      absencePct: s.vAttendanceSummary.absencePct,
    })
    .from(s.groupSubject)
    .innerJoin(s.subjects, eq(s.subjects.idSubject, s.groupSubject.idSubject))
    .innerJoin(s.schoolGroups, eq(s.schoolGroups.idGroup, s.groupSubject.idGroup))
    .leftJoin(s.teacherProfile, eq(s.teacherProfile.idTeacher, s.groupSubject.idTeacher))
    .leftJoin(teacherUser, eq(teacherUser.idUser, s.teacherProfile.idUser))
    .leftJoin(
      s.vAttendanceSummary,
      and(
        eq(s.vAttendanceSummary.idStudent, idStudent),
        eq(s.vAttendanceSummary.idGroup, s.groupSubject.idGroup),
        eq(s.vAttendanceSummary.idSubject, s.groupSubject.idSubject),
      ),
    )
    .where(eq(s.groupSubject.idGroup, idGroup))
    .orderBy(asc(s.subjects.subjectName));

  const incidents = await db()
    .select({ idSubject: s.attendance.idSubject, date: s.attendance.classDate, status: s.attendance.status })
    .from(s.attendance)
    .where(
      and(
        eq(s.attendance.idStudent, idStudent),
        eq(s.attendance.idGroup, idGroup),
        inArray(s.attendance.status, ["Absent", "Late", "Excused"]),
      ),
    )
    .orderBy(desc(s.attendance.classDate));

  return {
    groupName: rows[0]?.groupName ?? null,
    subjects: rows.map((r) => {
      const pct = toNumber(r.absencePct);
      return {
        idSubject: r.idSubject,
        subjectName: r.subjectName,
        teacherName: r.teacherFirst ? `${r.teacherFirst} ${r.teacherLast}` : null,
        sessions: r.sessions ?? 0,
        absences: r.absences ?? 0,
        lates: r.lates ?? 0,
        excused: r.excused ?? 0,
        absencePct: pct,
        atRisk: atRisk(pct),
        incidents: incidents
          .filter((i) => i.idSubject === r.idSubject)
          .map((i) => ({ date: i.date, status: i.status as "Absent" | "Late" | "Excused" })),
      };
    }),
  };
}

// ---------------------------------------------------------------------------
// Kárdex
// ---------------------------------------------------------------------------

/** Historial completo del alumno (todas sus materias cursadas o en curso), por periodo. */
export async function getKardex(idStudent: number) {
  const ctx = await studentContext(idStudent);
  if (!ctx) return null;
  const v = s.vKardex;
  const rows = await db().select().from(v).where(eq(v.idStudent, idStudent));
  const entries: KardexEntry[] = rows.map((r) => ({
    idPeriod: r.idPeriod!,
    periodName: r.periodName!,
    periodStart: r.periodStart!,
    periodStatus: r.periodStatus!,
    groupName: r.groupName!,
    groupSemester: r.groupSemester!,
    idSubject: r.idSubject!,
    subjectName: r.subjectName!,
    credits: r.credits!,
    subjectSemester: r.subjectSemester!,
    parcial1: toNumber(r.parcial1),
    parcial2: toNumber(r.parcial2),
    parcial3: toNumber(r.parcial3),
    finalGrade: toNumber(r.finalGrade),
    result: r.result as KardexResult,
  }));

  const [career] = await db()
    .select({ credits: sql<number>`coalesce(sum(${s.subjects.credits}), 0)::int` })
    .from(s.subjects)
    .where(and(eq(s.subjects.idCareer, ctx.idCareer), eq(s.subjects.status, "Active")));
  const [gpa] = await db()
    .select({ gpa: s.vStudentGpa.gpa })
    .from(s.vStudentGpa)
    .where(eq(s.vStudentGpa.idStudent, idStudent));

  return {
    periods: groupByPeriod(entries),
    credits: creditSummary(entries, career?.credits ?? 0),
    gpa: toNumber(gpa?.gpa ?? null),
  };
}

export type Kardex = NonNullable<Awaited<ReturnType<typeof getKardex>>>;

// ---------------------------------------------------------------------------
// Documentos
// ---------------------------------------------------------------------------

/** Periodo del grupo actual del alumno (para la constancia de estudios). */
export async function getCurrentPeriod(idStudent: number) {
  const rows = await db()
    .select({
      name: s.academicPeriod.name,
      startDate: s.academicPeriod.startDate,
      endDate: s.academicPeriod.endDate,
    })
    .from(s.studentProfile)
    .innerJoin(s.schoolGroups, eq(s.schoolGroups.idGroup, s.studentProfile.idGroup))
    .innerJoin(s.academicPeriod, eq(s.academicPeriod.idPeriod, s.schoolGroups.idPeriod))
    .where(eq(s.studentProfile.idStudent, idStudent))
    .limit(1);
  return rows[0] ?? null;
}
