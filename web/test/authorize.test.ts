import postgres from "postgres";
import { describe, expect, it } from "vitest";
import { authorizeStudent } from "@/lib/auth/authorize";

const PASSWORD = "password123";

async function exec(query: string) {
  const sql = postgres(process.env.DATABASE_URL!, { max: 1, onnotice: () => {} });
  await sql.unsafe(query);
  await sql.end();
}

describe("authorizeStudent", () => {
  it("un alumno entra con su correo (normalizado) y obtiene su id_student", async () => {
    const r = await authorizeStudent({ email: "  Student001@BISUNI.edu ", password: PASSWORD });
    expect(r).toEqual({
      ok: true,
      user: { idUser: 13, idStudent: 1, email: "student001@bisuni.edu", name: "Carlos Hernández" },
    });
  });

  it("contraseña incorrecta y correo inexistente dan el mismo motivo", async () => {
    expect(await authorizeStudent({ email: "student001@bisuni.edu", password: "otra" })).toEqual({ ok: false, reason: "invalid" });
    expect(await authorizeStudent({ email: "nadie@bisuni.edu", password: PASSWORD })).toEqual({ ok: false, reason: "invalid" });
    expect(await authorizeStudent({ email: "no-es-correo", password: PASSWORD })).toEqual({ ok: false, reason: "invalid" });
  });

  it("el personal no entra al portal web", async () => {
    expect(await authorizeStudent({ email: "juan.perez@bisuni.edu", password: PASSWORD })).toEqual({ ok: false, reason: "staff" });
    expect(await authorizeStudent({ email: "admin.master@bisuni.edu", password: PASSWORD })).toEqual({ ok: false, reason: "staff" });
  });

  it("un aspirante pendiente recibe un mensaje propio", async () => {
    expect(await authorizeStudent({ email: "aspirant01@bisuni.edu", password: PASSWORD })).toEqual({ ok: false, reason: "aspirant" });
  });

  it("un aspirante aceptado (ya alumno) sí entra", async () => {
    const r = await authorizeStudent({ email: "aspirant05@bisuni.edu", password: PASSWORD });
    expect(r.ok && r.user.idStudent).toBe(51);
  });

  it("una cuenta desactivada no entra", async () => {
    await exec("UPDATE users SET status = 'Inactive' WHERE email = 'student002@bisuni.edu'");
    expect(await authorizeStudent({ email: "student002@bisuni.edu", password: PASSWORD })).toEqual({ ok: false, reason: "inactive" });
  });

  it("un alumno con baja definitiva no entra; uno de otra generación sí", async () => {
    // Seed: student060 se dio de baja tras reprobar en 2026-1.
    expect(await authorizeStudent({ email: "student060@bisuni.edu", password: PASSWORD })).toEqual({ ok: false, reason: "dropped" });
    const r = await authorizeStudent({ email: "student051@bisuni.edu", password: PASSWORD });
    expect(r.ok && r.user.idStudent).toBe(53);
  });
});
