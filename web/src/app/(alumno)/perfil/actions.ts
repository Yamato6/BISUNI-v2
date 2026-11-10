"use server";

import { revalidatePath } from "next/cache";
import { requireStudent } from "@/lib/dal";
import { changePassword, updateName } from "@/lib/profile";
import { nameSchema, passwordSchema } from "@/lib/profile-rules";

export interface FormState {
  ok?: string;
  errors?: Record<string, string>;
}

function fieldErrors(issues: Array<{ path: PropertyKey[]; message: string }>): Record<string, string> {
  const errors: Record<string, string> = {};
  for (const i of issues) errors[String(i.path[0])] ??= i.message;
  return errors;
}

export async function updateNameAction(_prev: FormState, formData: FormData): Promise<FormState> {
  const { idUser } = await requireStudent();
  const parsed = nameSchema.safeParse({ firstName: formData.get("firstName"), lastName: formData.get("lastName") });
  if (!parsed.success) return { errors: fieldErrors(parsed.error.issues) };
  await updateName(idUser, parsed.data);
  revalidatePath("/", "layout"); // el nombre aparece en la barra lateral y en el inicio
  return { ok: "Tus datos se guardaron." };
}

export async function changePasswordAction(_prev: FormState, formData: FormData): Promise<FormState> {
  const { idUser } = await requireStudent();
  const parsed = passwordSchema.safeParse({
    current: formData.get("current") ?? "",
    next: formData.get("next") ?? "",
    confirm: formData.get("confirm") ?? "",
  });
  if (!parsed.success) return { errors: fieldErrors(parsed.error.issues) };
  const result = await changePassword(idUser, parsed.data);
  if (!result.ok) return { errors: { [result.field]: result.message } };
  return { ok: "Tu contraseña se cambió. Úsala la próxima vez que entres." };
}
