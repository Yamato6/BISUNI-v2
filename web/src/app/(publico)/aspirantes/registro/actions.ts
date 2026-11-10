"use server";

import { registrationSchema } from "@/lib/aspirant-rules";
import { registerAspirant } from "@/lib/aspirants";

export type RegistrationField = "firstName" | "lastName" | "curp" | "email" | "password" | "passwordConfirm" | "idCareer";

export interface RegistrationState {
  folio?: string;
  email?: string;
  errors?: Partial<Record<RegistrationField, string>>;
  message?: string;
  /** Lo capturado (sin contraseñas) para no perderlo si hay errores. */
  values?: Partial<Record<"firstName" | "lastName" | "curp" | "email" | "idCareer", string>>;
}

export async function registerAction(_prev: RegistrationState, formData: FormData): Promise<RegistrationState> {
  const raw = {
    firstName: String(formData.get("firstName") ?? ""),
    lastName: String(formData.get("lastName") ?? ""),
    curp: String(formData.get("curp") ?? ""),
    email: String(formData.get("email") ?? ""),
    password: String(formData.get("password") ?? ""),
    passwordConfirm: String(formData.get("passwordConfirm") ?? ""),
    idCareer: String(formData.get("idCareer") ?? ""),
  };
  const values = { firstName: raw.firstName, lastName: raw.lastName, curp: raw.curp, email: raw.email, idCareer: raw.idCareer };

  const parsed = registrationSchema.safeParse(raw);
  if (!parsed.success) {
    const errors: RegistrationState["errors"] = {};
    for (const issue of parsed.error.issues) {
      const field = issue.path[0] as RegistrationField;
      errors[field] ??= issue.message;
    }
    return { errors, values, message: "Revisa los campos marcados." };
  }

  const result = await registerAspirant(parsed.data);
  if (!result.ok) {
    return {
      errors: result.field ? { [result.field]: result.message } : undefined,
      message: result.message,
      values,
    };
  }
  return { folio: result.folio, email: parsed.data.email };
}
