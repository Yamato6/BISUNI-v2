import "server-only";
import { and, eq, ne } from "drizzle-orm";
import { redirect } from "next/navigation";
import { cache } from "react";
import { auth } from "@/auth";
import { db, schema } from "@/db";
import type { AcademicStatus } from "@/lib/student";

export interface StudentSession {
  idUser: number;
  idStudent: number;
  academicStatus: AcademicStatus;
}

/**
 * Lee la sesión y la valida contra la base: la cuenta sigue activa, sigue siendo de alumno, conserva
 * su perfil y no tiene baja definitiva. Si no, cierra la sesión (un render no puede borrar cookies,
 * así que redirige al Route Handler /salir).
 */
const loadStudent = cache(async (): Promise<StudentSession & { mustChangePassword: boolean }> => {
  const session = await auth();
  if (session?.user?.role !== "Student") redirect("/login");

  const { idUser, idStudent } = session.user;
  const rows = await db()
    .select({
      idStudent: schema.studentProfile.idStudent,
      academicStatus: schema.studentProfile.academicStatus,
      mustChangePassword: schema.users.mustChangePassword,
    })
    .from(schema.users)
    .innerJoin(schema.studentProfile, eq(schema.studentProfile.idUser, schema.users.idUser))
    .where(
      and(
        eq(schema.users.idUser, idUser),
        eq(schema.users.role, "Student"),
        eq(schema.users.status, "Active"),
        ne(schema.studentProfile.academicStatus, "Dropped"),
      ),
    )
    .limit(1);

  const row = rows[0];
  if (row?.idStudent !== idStudent) redirect("/salir?motivo=expirada");
  return { idUser, idStudent, academicStatus: row.academicStatus, mustChangePassword: row.mustChangePassword };
});

/**
 * Verificación SEGURA de la sesión del alumno (la del proxy solo lee la cookie). Toda página o
 * acción del alumno debe llamarla. Si control escolar le restableció la contraseña, lo manda a
 * crear una propia antes de continuar.
 */
export const requireStudent = cache(async (): Promise<StudentSession> => {
  const { mustChangePassword, ...student } = await loadStudent();
  if (mustChangePassword) redirect("/cambiar-contrasena");
  return student;
});

/** Para la página de cambio obligatorio: la misma validación, sin la redirección por contraseña temporal. */
export const requireStudentForPasswordChange = cache(async () => loadStudent());
