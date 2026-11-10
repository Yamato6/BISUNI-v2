import "server-only";
import bcrypt from "bcryptjs";
import { eq } from "drizzle-orm";
import { db, schema } from "@/db";
import type { NameInput, PasswordInput } from "@/lib/profile-rules";

const BCRYPT_COST = 10;

export async function updateName(idUser: number, data: NameInput): Promise<void> {
  await db()
    .update(schema.users)
    .set({ firstName: data.firstName, lastName: data.lastName })
    .where(eq(schema.users.idUser, idUser));
}

export type PasswordChangeResult = { ok: true } | { ok: false; field: "current"; message: string };

/** Cambia la contraseña solo si la actual es correcta (la v1 no la pedía). */
export async function changePassword(idUser: number, data: PasswordInput): Promise<PasswordChangeResult> {
  const rows = await db()
    .select({ hash: schema.users.passwordHash })
    .from(schema.users)
    .where(eq(schema.users.idUser, idUser))
    .limit(1);
  const hash = rows[0]?.hash;
  if (!hash || !(await bcrypt.compare(data.current, hash))) {
    return { ok: false, field: "current", message: "La contraseña actual no es correcta." };
  }
  await db()
    .update(schema.users)
    .set({ passwordHash: await bcrypt.hash(data.next, BCRYPT_COST) })
    .where(eq(schema.users.idUser, idUser));
  return { ok: true };
}
