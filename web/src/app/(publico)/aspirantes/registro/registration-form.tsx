"use client";

import Link from "next/link";
import { useActionState } from "react";
import { FormField, fieldProps } from "@/components/form-field";
import { MIN_PASSWORD } from "@/lib/aspirant-rules";
import { registerAction, type RegistrationState } from "./actions";

export function RegistrationForm({ careers }: { careers: Array<{ idCareer: number; name: string }> }) {
  const [state, action, pending] = useActionState<RegistrationState, FormData>(registerAction, {});
  const e = state.errors ?? {};
  const v = state.values ?? {};

  if (state.folio) {
    return (
      <div className="space-y-4" role="status">
        <h2 className="text-2xl font-bold text-brand-deep">¡Solicitud registrada!</h2>
        <p>Guarda tu folio: lo necesitarás, junto con tu CURP, para consultar el estatus de tu solicitud.</p>
        <p className="rounded-xl bg-brand-soft px-5 py-4 text-center">
          <span className="block text-sm text-muted">Tu folio</span>
          <span data-testid="folio" className="font-display text-4xl font-bold tracking-wider text-brand-deep">{state.folio}</span>
        </p>
        <p className="text-sm text-muted">
          Si te aceptan, entrarás al portal de alumnos con <strong>{state.email}</strong> y la contraseña que acabas de crear.
        </p>
        <Link href="/aspirantes/estatus" className="btn-primary">Consultar estatus</Link>
      </div>
    );
  }

  return (
    <form action={action} className="space-y-6" noValidate>
      {state.message && (
        <p role="alert" className="rounded-lg bg-bad-soft px-3 py-2 text-sm text-bad">{state.message}</p>
      )}

      <fieldset className="space-y-4">
        <legend className="mb-2 font-display text-lg font-bold">Datos personales</legend>
        <div className="grid gap-4 sm:grid-cols-2">
          <FormField id="firstName" label="Nombre(s)" error={e.firstName}>
            <input {...fieldProps("firstName", e.firstName)} defaultValue={v.firstName} autoComplete="given-name" required />
          </FormField>
          <FormField id="lastName" label="Apellidos" error={e.lastName}>
            <input {...fieldProps("lastName", e.lastName)} defaultValue={v.lastName} autoComplete="family-name" required />
          </FormField>
        </div>
        <FormField id="curp" label="CURP" error={e.curp} hint="18 caracteres, tal como aparece en tu documento.">
          <input
            {...fieldProps("curp", e.curp, "hint")}
            defaultValue={v.curp}
            maxLength={18}
            autoCapitalize="characters"
            spellCheck={false}
            className={`field uppercase ${e.curp ? "border-bad" : ""}`}
            required
          />
        </FormField>
      </fieldset>

      <fieldset className="space-y-4">
        <legend className="mb-2 font-display text-lg font-bold">Cuenta</legend>
        <FormField id="email" label="Correo electrónico" error={e.email} hint="Será tu usuario para entrar al portal si te aceptan.">
          <input {...fieldProps("email", e.email, "hint")} type="email" defaultValue={v.email} autoComplete="email" required />
        </FormField>
        <div className="grid gap-4 sm:grid-cols-2">
          <FormField id="password" label="Contraseña" error={e.password} hint={`Al menos ${MIN_PASSWORD} caracteres.`}>
            <input {...fieldProps("password", e.password, "hint")} type="password" autoComplete="new-password" required />
          </FormField>
          <FormField id="passwordConfirm" label="Confirmar contraseña" error={e.passwordConfirm}>
            <input {...fieldProps("passwordConfirm", e.passwordConfirm)} type="password" autoComplete="new-password" required />
          </FormField>
        </div>
      </fieldset>

      <fieldset className="space-y-4">
        <legend className="mb-2 font-display text-lg font-bold">Datos académicos</legend>
        <FormField id="idCareer" label="Carrera de interés" error={e.idCareer}>
          <select
            id="idCareer"
            name="idCareer"
            defaultValue={v.idCareer ?? ""}
            aria-invalid={e.idCareer ? true : undefined}
            aria-describedby={e.idCareer ? "idCareer-error" : undefined}
            className={`field ${e.idCareer ? "border-bad" : ""}`}
            required
          >
            <option value="" disabled>Elige una carrera</option>
            {careers.map((c) => (
              <option key={c.idCareer} value={c.idCareer}>{c.name}</option>
            ))}
          </select>
        </FormField>
      </fieldset>

      <button type="submit" disabled={pending} className="btn-primary w-full sm:w-auto">
        {pending ? "Enviando…" : "Enviar solicitud"}
      </button>
    </form>
  );
}
