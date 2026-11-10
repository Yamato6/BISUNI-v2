import bcrypt from "bcryptjs";
import postgres from "postgres";
import { describe, expect, it } from "vitest";
import { registrationSchema } from "@/lib/aspirant-rules";
import { activeCareers, findApplication, registerAspirant } from "@/lib/aspirants";
import { authorizeStudent } from "@/lib/auth/authorize";

async function query<T extends object>(text: string): Promise<T[]> {
  const sql = postgres(process.env.DATABASE_URL!, { max: 1, onnotice: () => {} });
  const rows = await sql.unsafe<T[]>(text);
  await sql.end();
  return rows;
}

function registration(overrides: object = {}) {
  return registrationSchema.parse({
    firstName: "Ana",
    lastName: "Gómez Ruiz",
    curp: "GOMA050101HASRRL09",
    email: "ana.gomez@correo.com",
    password: "segura123",
    passwordConfirm: "segura123",
    idCareer: "2",
    ...overrides,
  });
}

describe("aspirantes", () => {
  it("catálogo de carreras activas", async () => {
    const careers = await activeCareers();
    expect(careers.map((c) => c.name)).toContain("Information Technology");
  });

  it("registra cuenta + solicitud en una transacción y devuelve el folio", async () => {
    const r = await registerAspirant(registration());
    expect(r.ok).toBe(true);
    if (!r.ok) return;
    expect(r.folio).toBe(`ASP-${String(r.idAspirant).padStart(6, "0")}`);

    const [row] = await query<{ username: string; role: string; status: string; curp: string; hash: string }>(`
      SELECT u.username, u.role::text AS role, a.status::text AS status, a.curp, u.password_hash AS hash
      FROM aspirant_profile a JOIN users u ON u.id_user = a.id_user WHERE a.id_aspirant = ${r.idAspirant}`);
    expect(row).toMatchObject({ username: "goma050101hasrrl09", role: "Aspirant", status: "Pending", curp: "GOMA050101HASRRL09" });
    expect(await bcrypt.compare("segura123", row.hash)).toBe(true);

    // Mientras está pendiente, el login le avisa que su solicitud no ha sido aceptada.
    expect(await authorizeStudent({ email: "ana.gomez@correo.com", password: "segura123" })).toEqual({ ok: false, reason: "aspirant" });
  });

  it("correo o CURP repetidos dan mensajes claros y no dejan cuentas sueltas", async () => {
    const before = await query<{ n: string }>("SELECT count(*) AS n FROM users");
    expect(await registerAspirant(registration({ email: "STUDENT001@bisuni.edu", curp: "PERJ050505HASRRL01" }))).toEqual({
      ok: false,
      field: "email",
      message: "Ya existe una cuenta con ese correo.",
    });
    const dupCurp = await registerAspirant(registration({ email: "otro@correo.com", curp: "BEXA070312MASLND01" }));
    expect(dupCurp).toMatchObject({ ok: false, field: "curp" });
    const after = await query<{ n: string }>("SELECT count(*) AS n FROM users");
    expect(after[0].n).toBe(before[0].n);
  });

  it("carrera inexistente o inactiva", async () => {
    expect(await registerAspirant(registration({ idCareer: "999" }))).toMatchObject({ ok: false, field: "idCareer" });
  });

  it("estatus: requiere CURP y folio correctos", async () => {
    const app = await findApplication("BEXA070312MASLND01", 1);
    expect(app).toMatchObject({ folio: "ASP-000001", firstName: "Andrea", status: "Pending", careerName: "Information Technology", enrollmentDate: null });
    expect(await findApplication("BEXA070312MASLND01", 2)).toBeNull(); // folio de otra persona
    expect(await findApplication("IAXS061120HASBMM02", 1)).toBeNull();
  });

  it("estatus de un aspirante aceptado muestra su fecha de inscripción", async () => {
    const app = await findApplication("BAXC070101MASTLL05", 5);
    expect(app).toMatchObject({ status: "Accepted", enrollmentDate: "2025-09-01" });
  });
});
