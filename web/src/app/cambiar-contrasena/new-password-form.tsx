"use client";

import { useActionState } from "react";
import { FormField, fieldProps } from "@/components/form-field";
import { MIN_PASSWORD } from "@/lib/aspirant-rules";
import { setOwnPasswordAction, type NewPasswordState } from "./actions";

export function NewPasswordForm() {
  const [state, action, pending] = useActionState<NewPasswordState, FormData>(setOwnPasswordAction, {});
  const e = state.errors ?? {};
  return (
    <form action={action} className="space-y-4" noValidate>
      <FormField id="next" label="Nueva contraseña" error={e.next} hint={`Al menos ${MIN_PASSWORD} caracteres.`}>
        <input {...fieldProps("next", e.next, "hint")} type="password" autoComplete="new-password" required autoFocus />
      </FormField>
      <FormField id="confirm" label="Confirmar nueva contraseña" error={e.confirm}>
        <input {...fieldProps("confirm", e.confirm)} type="password" autoComplete="new-password" required />
      </FormField>
      <button type="submit" disabled={pending} className="btn-primary w-full">
        {pending ? "Guardando…" : "Crear mi contraseña"}
      </button>
    </form>
  );
}
