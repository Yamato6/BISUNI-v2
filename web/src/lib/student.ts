import "server-only";
import { and, asc, eq, inArray, sql } from "drizzle-orm";
import { alias } from "drizzle-orm/pg-core";
import { db, schema } from "@/db";
import { summarize, toNumber, type GradeSummary } from "@/lib/grades";
import type { ScheduleClass, TimeSlot, Weekday } from "@/lib/schedule";

const s = schema;
const teacherUser = alias(s.users, "teacher_user");
const tutorUser = alias(s.users, "tutor_user");

// ---------------------------------------------------------------------------
// Datos generales
// ---------------------------------------------------------------------------

export interface StudentOverview {
  firstName: string;
  lastName: string;
  email: string;
  username: string;
  careerName: string;
  semester: number;
  /** Promedio oficial registrado por control escolar (student_profile.gpa). */
  gpa: number;
  groupName: string | null;
  tutorName: string | null;
  enrollmentDate: string | null;
}

export async function getOverview(idStudent: number): Promise<StudentOverview | null> {
  const rows = await db()
    .select({
      firstName: s.users.firstName,
      lastName: s.users.lastName,
      email: s.users.email,
      username: s.users.username,
      careerName: s.career.careerName,
      semester: s.studentProfile.semester,
      gpa: s.studentProfile.gpa,
      groupName: s.schoolGroups.groupName,
      tutorFirst: tutorUser.firstName,
      tutorLast: tutorUser.lastName,
      enrollmentDate: s.enrollment.enrollmentDate,
    })
    .from(s.studentProfile)
    .innerJoin(s.users, eq(s.users.idUser, s.studentProfile.idUser))
    .innerJoin(s.career, eq(s.career.idCareer, s.studentProfile.idCareer))
    .leftJoin(s.schoolGroups, eq(s.schoolGroups.idGroup, s.studentProfile.idGroup))
    .leftJoin(s.teacherProfile, eq(s.teacherProfile.idTeacher, s.schoolGroups.idTutor))
    .leftJoin(tutorUser, eq(tutorUser.idUser, s.teacherProfile.idUser))
    .leftJoin(s.enrollment, eq(s.enrollment.idStudent, s.studentProfile.idStudent))
    .where(eq(s.studentProfile.idStudent, idStudent))
    .limit(1);
  const r = rows[0];
  if (!r) return null;
  return {
    firstName: r.firstName,
    lastName: r.lastName,
    email: r.email,
    username: r.username,
    careerName: r.careerName,
    semester: r.semester,
    gpa: toNumber(r.gpa) ?? 0,
    groupName: r.groupName,
    tutorName: r.tutorFirst ? `${r.tutorFirst} ${r.tutorLast}` : null,
    enrollmentDate: r.enrollmentDate,
  };
}

// ---------------------------------------------------------------------------
// Calificaciones
// ---------------------------------------------------------------------------

export interface SubjectGrade {
  idGroup: number;
  groupName: string;
  idSubject: number;
  subjectName: string;
  credits: number;
  teacherName: string | null;
  parcial1: number | null;
  parcial2: number | null;
  parcial3: number | null;
  /** Calculada por la base (columna generada); null si falta algún parcial. */
  finalGrade: number | null;
  /** true si es una materia del grupo actual del alumno. */
  current: boolean;
}

export interface StudentGrades {
  subjects: SubjectGrade[];
  summary: GradeSummary;
}

/**
 * Materias del grupo actual (tengan o no calificaciones) más las calificaciones que el alumno
 * tenga de otros grupos (p. ej. si lo cambiaron de grupo). Cada materia aparece una vez por
 * grupo y con el profesor que la imparte (group_subject.id_teacher): la v1 la duplicaba
 * cuando la materia tenía varios profesores habilitados.
 */
export async function getGrades(idStudent: number): Promise<StudentGrades> {
  const profile = await db()
    .select({ idGroup: s.studentProfile.idGroup })
    .from(s.studentProfile)
    .where(eq(s.studentProfile.idStudent, idStudent))
    .limit(1);
  const currentGroup = profile[0]?.idGroup ?? null;

  // Clases (grupo, materia) del alumno: las del grupo actual y las que ya tienen calificación.
  const graded = await db()
    .select({ idGroup: s.grades.idGroup, idSubject: s.grades.idSubject })
    .from(s.grades)
    .where(eq(s.grades.idStudent, idStudent));
  const current =
    currentGroup === null
      ? []
      : await db()
          .select({ idGroup: s.groupSubject.idGroup, idSubject: s.groupSubject.idSubject })
          .from(s.groupSubject)
          .where(eq(s.groupSubject.idGroup, currentGroup));

  const keys = new Set([...graded, ...current].map((c) => `${c.idGroup}:${c.idSubject}`));
  if (keys.size === 0) return { subjects: [], summary: summarize([]) };
  const groupIds = [...new Set([...graded, ...current].map((c) => c.idGroup))];

  const rows = await db()
    .select({
      idGroup: s.groupSubject.idGroup,
      groupName: s.schoolGroups.groupName,
      idSubject: s.subjects.idSubject,
      subjectName: s.subjects.subjectName,
      credits: s.subjects.credits,
      teacherFirst: teacherUser.firstName,
      teacherLast: teacherUser.lastName,
      parcial1: s.grades.parcial1,
      parcial2: s.grades.parcial2,
      parcial3: s.grades.parcial3,
      finalGrade: s.grades.finalGrade,
    })
    .from(s.groupSubject)
    .innerJoin(s.schoolGroups, eq(s.schoolGroups.idGroup, s.groupSubject.idGroup))
    .innerJoin(s.subjects, eq(s.subjects.idSubject, s.groupSubject.idSubject))
    .leftJoin(s.teacherProfile, eq(s.teacherProfile.idTeacher, s.groupSubject.idTeacher))
    .leftJoin(teacherUser, eq(teacherUser.idUser, s.teacherProfile.idUser))
    .leftJoin(
      s.grades,
      and(
        eq(s.grades.idStudent, idStudent),
        eq(s.grades.idGroup, s.groupSubject.idGroup),
        eq(s.grades.idSubject, s.groupSubject.idSubject),
      ),
    )
    .where(inArray(s.groupSubject.idGroup, groupIds))
    .orderBy(sql`${s.groupSubject.idGroup} = ${currentGroup ?? -1} DESC`, asc(s.subjects.subjectName));

  const subjects: SubjectGrade[] = rows
    .filter((r) => keys.has(`${r.idGroup}:${r.idSubject}`))
    .map((r) => ({
      idGroup: r.idGroup,
      groupName: r.groupName,
      idSubject: r.idSubject,
      subjectName: r.subjectName,
      credits: r.credits,
      teacherName: r.teacherFirst ? `${r.teacherFirst} ${r.teacherLast}` : null,
      parcial1: toNumber(r.parcial1),
      parcial2: toNumber(r.parcial2),
      parcial3: toNumber(r.parcial3),
      finalGrade: toNumber(r.finalGrade),
      current: r.idGroup === currentGroup,
    }));

  return { subjects, summary: summarize(subjects.map((x) => x.finalGrade)) };
}

// ---------------------------------------------------------------------------
// Profesores
// ---------------------------------------------------------------------------

export interface StudentTeacher {
  idTeacher: number;
  name: string;
  email: string;
  specialty: string | null;
  phone: string | null;
  /** Materias que imparte al grupo del alumno. */
  subjects: string[];
  /** true si además (o solo) es tutor del grupo. */
  tutor: boolean;
}

/**
 * Profesores del grupo actual: quienes imparten sus materias (group_subject.id_teacher) y su tutor.
 * En la v1 esta página mostraba datos fijos escritos en profesores.js.
 */
export async function getTeachers(idStudent: number): Promise<{ groupName: string | null; teachers: StudentTeacher[] }> {
  const profile = await db()
    .select({ idGroup: s.studentProfile.idGroup, groupName: s.schoolGroups.groupName, idTutor: s.schoolGroups.idTutor })
    .from(s.studentProfile)
    .leftJoin(s.schoolGroups, eq(s.schoolGroups.idGroup, s.studentProfile.idGroup))
    .where(eq(s.studentProfile.idStudent, idStudent))
    .limit(1);
  const group = profile[0];
  if (!group?.idGroup) return { groupName: null, teachers: [] };

  const teaching = await db()
    .select({ idTeacher: s.groupSubject.idTeacher, subjectName: s.subjects.subjectName })
    .from(s.groupSubject)
    .innerJoin(s.subjects, eq(s.subjects.idSubject, s.groupSubject.idSubject))
    .where(eq(s.groupSubject.idGroup, group.idGroup))
    .orderBy(asc(s.subjects.subjectName));

  const subjectsByTeacher = new Map<number, string[]>();
  for (const t of teaching) {
    if (t.idTeacher === null) continue;
    subjectsByTeacher.set(t.idTeacher, [...(subjectsByTeacher.get(t.idTeacher) ?? []), t.subjectName]);
  }
  const ids = [...new Set([...subjectsByTeacher.keys(), ...(group.idTutor ? [group.idTutor] : [])])];
  if (ids.length === 0) return { groupName: group.groupName, teachers: [] };

  const rows = await db()
    .select({
      idTeacher: s.teacherProfile.idTeacher,
      firstName: teacherUser.firstName,
      lastName: teacherUser.lastName,
      email: teacherUser.email,
      specialty: s.teacherProfile.specialty,
      phone: s.teacherProfile.phone,
    })
    .from(s.teacherProfile)
    .innerJoin(teacherUser, eq(teacherUser.idUser, s.teacherProfile.idUser))
    .where(inArray(s.teacherProfile.idTeacher, ids))
    .orderBy(asc(teacherUser.lastName), asc(teacherUser.firstName));

  return {
    groupName: group.groupName,
    teachers: rows.map((r) => ({
      idTeacher: r.idTeacher,
      name: `${r.firstName} ${r.lastName}`,
      email: r.email,
      specialty: r.specialty,
      phone: r.phone,
      subjects: subjectsByTeacher.get(r.idTeacher) ?? [],
      tutor: r.idTeacher === group.idTutor,
    })),
  };
}

// ---------------------------------------------------------------------------
// Horario
// ---------------------------------------------------------------------------

export interface StudentSchedule {
  groupName: string | null;
  slots: TimeSlot[];
  classes: ScheduleClass[];
}

/** Horario del grupo actual del alumno (tabla schedule; el profesor sale de group_subject). */
export async function getSchedule(idStudent: number): Promise<StudentSchedule> {
  const slots = await db()
    .select({
      idSlot: s.timeSlots.idSlot,
      slotNumber: s.timeSlots.slotNumber,
      startTime: s.timeSlots.startTime,
      endTime: s.timeSlots.endTime,
    })
    .from(s.timeSlots)
    .orderBy(asc(s.timeSlots.slotNumber));

  const profile = await db()
    .select({ idGroup: s.studentProfile.idGroup, groupName: s.schoolGroups.groupName })
    .from(s.studentProfile)
    .leftJoin(s.schoolGroups, eq(s.schoolGroups.idGroup, s.studentProfile.idGroup))
    .where(eq(s.studentProfile.idStudent, idStudent))
    .limit(1);
  const group = profile[0];
  if (!group?.idGroup) return { groupName: null, slots, classes: [] };

  const rows = await db()
    .select({
      day: s.schedule.day,
      idSlot: s.schedule.idSlot,
      idSubject: s.subjects.idSubject,
      subjectName: s.subjects.subjectName,
      teacherFirst: teacherUser.firstName,
      teacherLast: teacherUser.lastName,
    })
    .from(s.schedule)
    .innerJoin(s.subjects, eq(s.subjects.idSubject, s.schedule.idSubject))
    .innerJoin(s.teacherProfile, eq(s.teacherProfile.idTeacher, s.schedule.idTeacher))
    .innerJoin(teacherUser, eq(teacherUser.idUser, s.teacherProfile.idUser))
    .where(eq(s.schedule.idGroup, group.idGroup));

  return {
    groupName: group.groupName,
    slots,
    classes: rows.map((r) => ({
      day: r.day as Weekday,
      idSlot: r.idSlot,
      idSubject: r.idSubject,
      subjectName: r.subjectName,
      teacherName: `${r.teacherFirst} ${r.teacherLast}`,
    })),
  };
}

