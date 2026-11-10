import { describe, expect, it } from "vitest";
import { formatFolio, parseFolio, registrationSchema, statusQuerySchema } from "./aspirant-rules";

const valid = {
  firstName: " Ana ",
  lastName: "Gómez Ruiz",
  curp: "goma050101hasrrl09",
  email: " Ana.Gomez@Correo.com ",
  password: "segura123",
  passwordConfirm: "segura123",
  idCareer: "2",
};

describe("folio", () => {
  it("formato ASP- con 6 dígitos", () => {
    expect(formatFolio(1)).toBe("ASP-000001");
    expect(formatFolio(123456)).toBe("ASP-123456");
  });

  it("acepta variantes al escribirlo", () => {
    expect(parseFolio("ASP-000123")).toBe(123);
    expect(parseFolio("asp000123")).toBe(123);
    expect(parseFolio(" 123 ")).toBe(123);
    expect(parseFolio("ASP-0")).toBeNull();
    expect(parseFolio("ABC-1")).toBeNull();
    expect(parseFolio("1234567")).toBeNull();
  });
});

describe("registro", () => {
  it("normaliza nombre, CURP (mayúsculas) y correo (minúsculas)", () => {
    const r = registrationSchema.parse(valid);
    expect(r).toMatchObject({ firstName: "Ana", curp: "GOMA050101HASRRL09", email: "ana.gomez@correo.com", idCareer: 2 });
  });

  it("rechaza CURP inválida, contraseña corta, confirmación distinta y carrera vacía", () => {
    const fields = (data: object) =>
      registrationSchema.safeParse({ ...valid, ...data }).error?.issues.map((i) => i.path[0]);
    expect(fields({ curp: "GOMA050101HASRRL0" })).toContain("curp");
    expect(fields({ curp: "GOMA050101ZASRRL09" })).toContain("curp"); // sexo inválido
    expect(fields({ password: "corta", passwordConfirm: "corta" })).toContain("password");
    expect(fields({ passwordConfirm: "otra12345" })).toContain("passwordConfirm");
    expect(fields({ idCareer: "" })).toContain("idCareer");
    expect(fields({ email: "sin-arroba" })).toContain("email");
  });
});

describe("consulta de estatus", () => {
  it("CURP + folio", () => {
    expect(statusQuerySchema.parse({ curp: "bexa070312maslnd01", folio: "asp-1" })).toEqual({ curp: "BEXA070312MASLND01", folio: 1 });
    expect(statusQuerySchema.safeParse({ curp: "BEXA070312MASLND01", folio: "xx" }).success).toBe(false);
  });
});
