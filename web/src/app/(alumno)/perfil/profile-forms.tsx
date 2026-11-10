"use client";

import { useActionState, useEffect, useRef } from "react";
import { FormField, fieldProps } from "@/components/form-field";
import { MIN_PASSWORD } from "@/lib/aspirant-rules";
import { changePasswordAction, updateNameAction, type FormState } from "./actions";

function Feedback({ state }: { state: FormState }) {
  if (!state.ok) return null;
  return (
    <p role="status" className="rounded-lg bg-ok-soft px-3 py-2 text-sm text-ok">{state.ok}</p>
  );
}

export function NameForm({ firstName, lastName }: { firstName: string; lastName: string }) {
  const [state, action, pending] = useActionState<FormState, FormData>(updateNameAction, {});
  const e = state.errors ?? {};
  return (
    <form action={action} className="space-y-4" noValidate>
      <div className="grid gap-4 sm:grid-cols-2">
        <FormField id="firstName" label="Nombre(s)" error={e.firstName}>
          <input {...fieldProps("firstName", e.firstName)} defaultValue={firstName} autoComplete="given-name" required />
        </FormField>
        <FormField id="lastName" label="Apellidos" error={e.lastName}>
          <input {...fieldProps("lastName", e.lastName)} defaultValue={lastName} autoComplete="family-name" required />
        </FormField>
      </div>
      <Feedback state={state} />
      <button type="submit" disabled={pending} className="btn-primary">
        {pending ? "Guardando…" : "Guardar datos"}
      </button>
    </form>
  );
}

export function PasswordForm() {
  const [state, action, pending] = useActionState<FormState, FormData>(changePasswordAction, {});
  const formRef = useRef<HTMLFormElement>(null);
  const e = state.errors ?? {};

  // Tras un cambio exitoso, se limpian los campos de contraseña.
  useEffect(() => {
    if (state.ok) formRef.current?.reset();
  }, [state]);

  return (
    <form ref={formRef} action={action} className="space-y-4" noValidate>
      <FormField id="current" label="Contraseña actual" error={e.current}>
        <input {...fieldProps("current", e.current)} type="password" autoComplete="current-password" required />
      </FormField>
      <div className="grid gap-4 sm:grid-cols-2">
        <FormField id="next" label="Nueva contraseña" error={e.next} hint={`Al menos ${MIN_PASSWORD} caracteres.`}>
          <input {...fieldProps("next", e.next, "hint")} type="password" autoComplete="new-password" required />
        </FormField>
        <FormField id="confirm" label="Confirmar nueva contraseña" error={e.confirm}>
          <input {...fieldProps("confirm", e.confirm)} type="password" autoComplete="new-password" required />
        </FormField>
      </div>
      <Feedback state={state} />
      <button type="submit" disabled={pending} className="btn-primary">
        {pending ? "Cambiando…" : "Cambiar contraseña"}
      </button>
    </form>
  );
}
