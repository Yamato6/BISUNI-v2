"use client";

import { useActionState } from "react";
import { loginAction, type LoginState } from "./actions";

export function LoginForm({ notice }: { notice?: string }) {
  const [state, action, pending] = useActionState<LoginState, FormData>(loginAction, {});

  return (
    <form action={action} className="space-y-4" noValidate>
      {notice && !state.error && (
        <p role="status" className="rounded-lg bg-warn-soft px-3 py-2 text-sm text-warn">
          {notice}
        </p>
      )}
      <div>
        <label htmlFor="email" className="label">Correo institucional</label>
        <input
          id="email"
          name="email"
          type="email"
          autoComplete="username"
          required
          defaultValue={state.email}
          placeholder="nombre@bisuni.edu"
          className="field"
        />
      </div>
      <div>
        <label htmlFor="password" className="label">Contraseña</label>
        <input id="password" name="password" type="password" autoComplete="current-password" required className="field" />
      </div>
      {state.error && (
        <p role="alert" className="rounded-lg bg-bad-soft px-3 py-2 text-sm text-bad">
          {state.error}
        </p>
      )}
      <button type="submit" disabled={pending} className="btn-primary w-full">
        {pending ? "Verificando…" : "Entrar"}
      </button>
    </form>
  );
}
