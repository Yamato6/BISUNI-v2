"use server";

import { redirect } from "next/navigation";
import { requireStudentForPasswordChange } from "@/lib/dal";
import { setOwnPassword } from "@/lib/profile";
import { newPasswordSchema } from "@/lib/profile-rules";

export interface NewPasswordState {
  errors?: Record<string, string>;
  message?: string;
}

export async function setOwnPasswordAction(_prev: NewPasswordState, formData: FormData): Promise<NewPasswordState> {
  const { idUser, mustChangePassword } = await requireStudentForPasswordChange();
  if (!mustChangePassword) redirect("/dashboard");
  const parsed = newPasswordSchema.safeParse({ next: formData.get("next") ?? "", confirm: formData.get("confirm") ?? "" });
  if (!parsed.success) {
    const errors: Record<string, string> = {};
    for (const i of parsed.error.issues) errors[String(i.path[0])] ??= i.message;
    return { errors };
  }
  const result = await setOwnPassword(idUser, parsed.data.next);
  if (!result.ok) return { errors: { next: result.message } };
  redirect("/dashboard?contrasena=nueva");
}
