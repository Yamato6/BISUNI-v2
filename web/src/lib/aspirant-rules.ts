import { z } from "zod";

/**
 * Reglas de aspirantes que no dependen del servidor (se usan en formularios y en pruebas).
 * El formato de la CURP es el mismo CHECK de la base (aspirant_curp_format).
 */
export const CURP_REGEX = /^[A-Z]{4}[0-9]{6}[HMX][A-Z]{5}[A-Z0-9][0-9]$/;
export const MIN_PASSWORD = 8;

/** Folio visible: "ASP-" + id_aspirant con 6 dígitos (ej. ASP-000123). */
export function formatFolio(idAspirant: number): string {
  return `ASP-${String(idAspirant).padStart(6, "0")}`;
}

/** Acepta "ASP-000123", "asp000123", "ASP-123" o "123". Devuelve el id o null. */
export function parseFolio(text: string): number | null {
  const m = /^(?:ASP-?)?0*(\d{1,6})$/i.exec(text.trim());
  if (!m) return null;
  const id = Number(m[1]);
  return id > 0 ? id : null;
}

const curpField = z
  .string()
  .trim()
  .toUpperCase()
  .pipe(z.string().length(18, { error: "La CURP tiene 18 caracteres." }).regex(CURP_REGEX, { error: "La CURP no tiene un formato válido." }));

const nameField = (label: string) =>
  z.string().trim().min(1, { error: `Escribe ${label}.` }).max(50, { error: `Máximo 50 caracteres en ${label}.` });

export const registrationSchema = z
  .object({
    firstName: nameField("tu nombre"),
    lastName: nameField("tus apellidos"),
    curp: curpField,
    email: z
      .string()
      .trim()
      .toLowerCase()
      .pipe(z.email({ error: "Escribe un correo válido." }).max(150, { error: "El correo es demasiado largo." })),
    password: z
      .string()
      .min(MIN_PASSWORD, { error: `La contraseña debe tener al menos ${MIN_PASSWORD} caracteres.` })
      .max(72, { error: "La contraseña es demasiado larga." }),
    passwordConfirm: z.string(),
    idCareer: z.coerce.number({ error: "Elige una carrera." }).int().positive({ error: "Elige una carrera." }),
  })
  .refine((d) => d.password === d.passwordConfirm, {
    path: ["passwordConfirm"],
    error: "Las contraseñas no coinciden.",
  });

export type RegistrationInput = z.input<typeof registrationSchema>;
export type Registration = z.output<typeof registrationSchema>;

export const statusQuerySchema = z.object({
  curp: curpField,
  folio: z
    .string()
    .trim()
    .transform((v, ctx) => {
      const id = parseFolio(v);
      if (id === null) {
        ctx.addIssue({ code: "custom", message: "El folio tiene la forma ASP-000123." });
        return z.NEVER;
      }
      return id;
    }),
});

export type ApplicationStatus = "Pending" | "Accepted" | "Rejected";

export const STATUS_INFO: Record<ApplicationStatus, { label: string; detail: string }> = {
  Pending: {
    label: "En revisión",
    detail: "Tu solicitud está registrada y control escolar la revisará. Vuelve a consultar más adelante.",
  },
  Accepted: {
    label: "Aceptado",
    detail: "¡Felicidades! Ya eres alumno. Entra al portal de alumnos con el correo y la contraseña de tu registro.",
  },
  Rejected: {
    label: "No aceptado",
    detail: "Tu solicitud no fue aceptada en esta ocasión. Para más información acude a control escolar.",
  },
};
