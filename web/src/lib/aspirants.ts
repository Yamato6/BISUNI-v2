import "server-only";
import bcrypt from "bcryptjs";
import { and, asc, eq } from "drizzle-orm";
import { db, schema } from "@/db";
import { formatFolio, type ApplicationStatus, type Registration } from "@/lib/aspirant-rules";

const s = schema;
const BCRYPT_COST = 10;

export interface Career {
  idCareer: number;
  name: string;
}

export async function activeCareers(): Promise<Career[]> {
  return db()
    .select({ idCareer: s.career.idCareer, name: s.career.careerName })
    .from(s.career)
    .where(eq(s.career.status, "Active"))
    .orderBy(asc(s.career.careerName));
}

export type RegisterResult =
  | { ok: true; folio: string; idAspirant: number }
  | { ok: false; field?: "email" | "curp" | "idCareer"; message: string };

/**
 * Registra a un aspirante: crea su cuenta (rol Aspirant, contraseña con bcrypt) y su solicitud
 * en una sola transacción. Es la misma cuenta con la que entrará como alumno si lo aceptan.
 * El username es la CURP en minúsculas (única, igual que la CURP).
 */
export async function registerAspirant(data: Registration): Promise<RegisterResult> {
  const career = await db()
    .select({ id: s.career.idCareer })
    .from(s.career)
    .where(and(eq(s.career.idCareer, data.idCareer), eq(s.career.status, "Active")))
    .limit(1);
  if (career.length === 0) return { ok: false, field: "idCareer", message: "Elige una carrera de la lista." };

  const passwordHash = await bcrypt.hash(data.password, BCRYPT_COST);
  try {
    const idAspirant = await db().transaction(async (tx) => {
      const [user] = await tx
        .insert(s.users)
        .values({
          username: data.curp.toLowerCase(),
          passwordHash,
          email: data.email,
          firstName: data.firstName,
          lastName: data.lastName,
          role: "Aspirant",
        })
        .returning({ idUser: s.users.idUser });
      const [aspirant] = await tx
        .insert(s.aspirantProfile)
        .values({ idUser: user.idUser, curp: data.curp, idCareer: data.idCareer })
        .returning({ idAspirant: s.aspirantProfile.idAspirant });
      return aspirant.idAspirant;
    });
    return { ok: true, idAspirant, folio: formatFolio(idAspirant) };
  } catch (error) {
    const constraint = constraintOf(error);
    if (constraint === "users_email_key") {
      return { ok: false, field: "email", message: "Ya existe una cuenta con ese correo." };
    }
    if (constraint === "aspirant_profile_curp_key" || constraint === "users_username_key") {
      return {
        ok: false,
        field: "curp",
        message: "Ya hay una solicitud con esa CURP. Consulta su estatus con tu folio.",
      };
    }
    if (constraint === "aspirant_curp_format") {
      return { ok: false, field: "curp", message: "La CURP no tiene un formato válido." };
    }
    throw error;
  }
}

/** Nombre de la restricción violada (Drizzle envuelve el error de postgres en `cause`). */
function constraintOf(error: unknown): string | undefined {
  let current: unknown = error;
  for (let i = 0; i < 4 && current; i++) {
    const e = current as { constraint_name?: string; cause?: unknown };
    if (e.constraint_name) return e.constraint_name;
    current = e.cause;
  }
  return undefined;
}

export interface Application {
  folio: string;
  firstName: string;
  lastName: string;
  careerName: string;
  applicationDate: string;
  status: ApplicationStatus;
  enrollmentDate: string | null;
}

/** Busca una solicitud por CURP Y folio (la v1 bastaba con la CURP). */
export async function findApplication(curp: string, idAspirant: number): Promise<Application | null> {
  const rows = await db()
    .select({
      idAspirant: s.aspirantProfile.idAspirant,
      firstName: s.users.firstName,
      lastName: s.users.lastName,
      careerName: s.career.careerName,
      applicationDate: s.aspirantProfile.applicationDate,
      status: s.aspirantProfile.status,
      enrollmentDate: s.enrollment.enrollmentDate,
    })
    .from(s.aspirantProfile)
    .innerJoin(s.users, eq(s.users.idUser, s.aspirantProfile.idUser))
    .innerJoin(s.career, eq(s.career.idCareer, s.aspirantProfile.idCareer))
    .leftJoin(s.enrollment, eq(s.enrollment.idAspirant, s.aspirantProfile.idAspirant))
    .where(and(eq(s.aspirantProfile.curp, curp), eq(s.aspirantProfile.idAspirant, idAspirant)))
    .limit(1);
  const r = rows[0];
  if (!r) return null;
  return {
    folio: formatFolio(r.idAspirant),
    firstName: r.firstName,
    lastName: r.lastName,
    careerName: r.careerName,
    applicationDate: r.applicationDate,
    status: r.status,
    enrollmentDate: r.enrollmentDate,
  };
}
