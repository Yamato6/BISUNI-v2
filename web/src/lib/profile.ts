import "server-only";
import bcrypt from "bcryptjs";
import { and, eq, sql } from "drizzle-orm";
import { db, schema } from "@/db";
import type { NameInput, PasswordInput } from "@/lib/profile-rules";

const BCRYPT_COST = 10;

type Tx = Parameters<Parameters<ReturnType<typeof db>["transaction"]>[0]>[0];

/**
 * Corre `work` en una transacción que identifica al alumno ante la bitácora: los triggers de
 * audit_log leen `bisuni.actor` (igual que en el escritorio).
 */
export async function asActor<T>(idUser: number, work: (tx: Tx) => Promise<T>): Promise<T> {
  return db().transaction(async (tx) => {
    await tx.execute(sql`SELECT set_config('bisuni.actor', ${String(idUser)}, true)`);
    return work(tx);
  });
}

export async function updateName(idUser: number, data: NameInput): Promise<void> {
  await asActor(idUser, (tx) =>
    tx
      .update(schema.users)
      .set({ firstName: data.firstName, lastName: data.lastName })
      .where(eq(schema.users.idUser, idUser)),
  );
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
  const nextHash = await bcrypt.hash(data.next, BCRYPT_COST);
  await asActor(idUser, (tx) =>
    tx
      .update(schema.users)
      .set({ passwordHash: nextHash, mustChangePassword: false })
      .where(eq(schema.users.idUser, idUser)),
  );
  return { ok: true };
}

/**
 * Contraseña propia después de un restablecimiento: el alumno ya entró con la temporal, así que no
 * se le vuelve a pedir. Solo procede si la cuenta sigue marcada con must_change_password.
 * También rechaza que la nueva sea igual a la temporal.
 */
export async function setOwnPassword(
  idUser: number,
  next: string,
): Promise<{ ok: true } | { ok: false; message: string }> {
  const rows = await db()
    .select({ hash: schema.users.passwordHash, must: schema.users.mustChangePassword })
    .from(schema.users)
    .where(eq(schema.users.idUser, idUser))
    .limit(1);
  const current = rows[0];
  if (!current?.must) return { ok: false, message: "Tu contraseña ya no es temporal. Cámbiala desde Mi perfil." };
  if (await bcrypt.compare(next, current.hash)) {
    return { ok: false, message: "La nueva contraseña debe ser distinta de la temporal." };
  }
  const nextHash = await bcrypt.hash(next, BCRYPT_COST);
  await asActor(idUser, (tx) =>
    tx
      .update(schema.users)
      .set({ passwordHash: nextHash, mustChangePassword: false })
      .where(and(eq(schema.users.idUser, idUser), eq(schema.users.mustChangePassword, true))),
  );
  return { ok: true };
}
