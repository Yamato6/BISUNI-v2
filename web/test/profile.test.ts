import { describe, expect, it } from "vitest";
import { authorizeStudent } from "@/lib/auth/authorize";
import { changePassword, updateName } from "@/lib/profile";
import { nameSchema, passwordSchema } from "@/lib/profile-rules";
import { getOverview, getTeachers } from "@/lib/student";

describe("perfil", () => {
  it("valida nombre y contraseña", () => {
    expect(nameSchema.safeParse({ firstName: " ", lastName: "X" }).success).toBe(false);
    const issues = (d: object) => passwordSchema.safeParse(d).error?.issues.map((i) => i.path[0]);
    expect(issues({ current: "", next: "nueva12345", confirm: "nueva12345" })).toContain("current");
    expect(issues({ current: "a", next: "corta", confirm: "corta" })).toContain("next");
    expect(issues({ current: "a", next: "nueva12345", confirm: "otra12345" })).toContain("confirm");
    expect(issues({ current: "password123", next: "password123", confirm: "password123" })).toContain("next");
  });

  it("actualiza nombre y apellidos", async () => {
    await updateName(13, { firstName: "Carlos Iván", lastName: "Hernández" });
    expect((await getOverview(1))?.firstName).toBe("Carlos Iván");
  });

  it("cambiar la contraseña exige la actual (la v1 no la pedía)", async () => {
    expect(await changePassword(14, { current: "equivocada", next: "nueva12345", confirm: "nueva12345" })).toEqual({
      ok: false,
      field: "current",
      message: "La contraseña actual no es correcta.",
    });
    expect(await changePassword(14, { current: "password123", next: "nueva12345", confirm: "nueva12345" })).toEqual({ ok: true });
    expect((await authorizeStudent({ email: "student002@bisuni.edu", password: "nueva12345" })).ok).toBe(true);
    expect(await authorizeStudent({ email: "student002@bisuni.edu", password: "password123" })).toEqual({ ok: false, reason: "invalid" });
  });
});

describe("profesores del alumno", () => {
  it("quienes imparten las materias del grupo, con el tutor marcado", async () => {
    const { groupName, teachers } = await getTeachers(1);
    expect(groupName).toBe("IT-1A");
    expect(teachers.map((t) => [t.name, t.subjects, t.tutor])).toEqual([
      ["María García", ["Discrete Mathematics"], false],
      ["Juan Pérez", ["Programming Fundamentals"], true],
    ]);
    expect(teachers[1]).toMatchObject({ email: "juan.perez@bisuni.edu", specialty: "Software Engineering" });
  });

  it("alumno sin grupo", async () => {
    expect(await getTeachers(51)).toEqual({ groupName: null, teachers: [] });
  });
});
