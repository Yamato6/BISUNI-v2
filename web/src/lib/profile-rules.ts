import { z } from "zod";
import { MIN_PASSWORD } from "@/lib/aspirant-rules";

const nameField = (label: string) =>
  z.string().trim().min(1, { error: `Escribe ${label}.` }).max(50, { error: `Máximo 50 caracteres en ${label}.` });

export const nameSchema = z.object({
  firstName: nameField("tu nombre"),
  lastName: nameField("tus apellidos"),
});

export const passwordSchema = z
  .object({
    current: z.string().min(1, { error: "Escribe tu contraseña actual." }),
    next: z
      .string()
      .min(MIN_PASSWORD, { error: `La nueva contraseña debe tener al menos ${MIN_PASSWORD} caracteres.` })
      .max(72, { error: "La contraseña es demasiado larga." }),
    confirm: z.string(),
  })
  .refine((d) => d.next === d.confirm, { path: ["confirm"], error: "Las contraseñas no coinciden." })
  .refine((d) => d.next !== d.current, { path: ["next"], error: "La nueva contraseña debe ser distinta de la actual." });

/** Contraseña propia tras un restablecimiento (ya entró con la temporal: no se pide la actual). */
export const newPasswordSchema = z
  .object({
    next: z
      .string()
      .min(MIN_PASSWORD, { error: `La nueva contraseña debe tener al menos ${MIN_PASSWORD} caracteres.` })
      .max(72, { error: "La contraseña es demasiado larga." }),
    confirm: z.string(),
  })
  .refine((d) => d.next === d.confirm, { path: ["confirm"], error: "Las contraseñas no coinciden." });

export type NameInput = z.output<typeof nameSchema>;
export type PasswordInput = z.output<typeof passwordSchema>;
