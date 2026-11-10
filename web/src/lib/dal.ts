import "server-only";
import { and, eq } from "drizzle-orm";
import { redirect } from "next/navigation";
import { cache } from "react";
import { auth } from "@/auth";
import { db, schema } from "@/db";

export interface StudentSession {
  idUser: number;
  idStudent: number;
}

/**
 * Verificación SEGURA de la sesión del alumno (la del proxy solo lee la cookie).
 * Comprueba contra la base que la cuenta siga activa, siga siendo de alumno y conserve su perfil.
 * Se memoriza por render con `cache`. Toda página o acción del alumno debe llamarla.
 */
export const requireStudent = cache(async (): Promise<StudentSession> => {
  const session = await auth();
  if (session?.user?.role !== "Student") redirect("/login");

  const { idUser, idStudent } = session.user;
  const rows = await db()
    .select({ idStudent: schema.studentProfile.idStudent })
    .from(schema.users)
    .innerJoin(schema.studentProfile, eq(schema.studentProfile.idUser, schema.users.idUser))
    .where(
      and(
        eq(schema.users.idUser, idUser),
        eq(schema.users.role, "Student"),
        eq(schema.users.status, "Active"),
      ),
    )
    .limit(1);

  if (rows[0]?.idStudent !== idStudent) {
    // La cookie dice "alumno" pero la base ya no: cerrar la sesión (no se puede desde un render).
    redirect("/salir?motivo=expirada");
  }
  return { idUser, idStudent };
});
