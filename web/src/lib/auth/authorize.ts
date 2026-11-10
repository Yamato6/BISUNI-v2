import "server-only";
import bcrypt from "bcryptjs";
import { eq } from "drizzle-orm";
import { z } from "zod";
import { db, schema } from "@/db";

/**
 * Hash de relleno: si el correo no existe se compara contra él, para que la respuesta tarde
 * lo mismo que con una contraseña incorrecta y no se pueda saber qué correos existen.
 * (Es el hash de "password123" del seed; nunca coincide con una contraseña distinta.)
 */
const DUMMY_HASH = "$2b$10$dBVhXUfjbLZ./s1iqnh7Z.QTdJWw1Umm1yoO2LWON8mQ71.PIQvSO";

export const loginSchema = z.object({
  email: z
    .string()
    .trim()
    .toLowerCase()
    .pipe(z.email({ error: "Escribe un correo válido." })),
  password: z.string().min(1, { error: "Escribe tu contraseña." }).max(72),
});

export type LoginInput = z.input<typeof loginSchema>;

export interface StudentIdentity {
  idUser: number;
  idStudent: number;
  email: string;
  name: string;
}

/** Motivos de rechazo con mensaje propio; credenciales incorrectas siempre es "invalid". */
export type LoginFailure = "invalid" | "inactive" | "aspirant" | "staff" | "no_profile";

export type AuthorizeResult = { ok: true; user: StudentIdentity } | { ok: false; reason: LoginFailure };

export const LOGIN_MESSAGES: Record<LoginFailure, string> = {
  invalid: "Correo o contraseña incorrectos.",
  inactive: "Tu cuenta está desactivada. Acude a control escolar.",
  aspirant:
    "Tu solicitud de admisión todavía no ha sido aceptada. Consulta su estatus con tu CURP y tu folio.",
  staff: "El portal web es para alumnos. El personal usa la aplicación de escritorio.",
  no_profile: "Tu cuenta no tiene un perfil de alumno. Acude a control escolar.",
};

/** Verifica credenciales de un alumno. Solo el rol Student entra al portal. */
export async function authorizeStudent(raw: unknown): Promise<AuthorizeResult> {
  const parsed = loginSchema.safeParse(raw);
  if (!parsed.success) return { ok: false, reason: "invalid" };
  const { email, password } = parsed.data;

  const rows = await db()
    .select({
      idUser: schema.users.idUser,
      email: schema.users.email,
      firstName: schema.users.firstName,
      lastName: schema.users.lastName,
      role: schema.users.role,
      status: schema.users.status,
      passwordHash: schema.users.passwordHash,
      idStudent: schema.studentProfile.idStudent,
    })
    .from(schema.users)
    .leftJoin(schema.studentProfile, eq(schema.studentProfile.idUser, schema.users.idUser))
    .where(eq(schema.users.email, email))
    .limit(1);

  const user = rows[0];
  const matches = await bcrypt.compare(password, user?.passwordHash ?? DUMMY_HASH);
  if (!user || !matches) return { ok: false, reason: "invalid" };
  if (user.status !== "Active") return { ok: false, reason: "inactive" };
  if (user.role === "Aspirant") return { ok: false, reason: "aspirant" };
  if (user.role !== "Student") return { ok: false, reason: "staff" };
  if (user.idStudent === null) return { ok: false, reason: "no_profile" };

  return {
    ok: true,
    user: {
      idUser: user.idUser,
      idStudent: user.idStudent,
      email: user.email,
      name: `${user.firstName} ${user.lastName}`,
    },
  };
}
