"use client";

import Link from "next/link";
import { useActionState } from "react";
import { STATUS_INFO } from "@/lib/aspirant-rules";
import { statusAction, type StatusState } from "./actions";

const BADGE = {
  Pending: "bg-warn-soft text-warn",
  Accepted: "bg-ok-soft text-ok",
  Rejected: "bg-bad-soft text-bad",
} as const;

function formatDate(iso: string) {
  const [y, m, d] = iso.split("-");
  return `${d}/${m}/${y}`;
}

export function StatusForm() {
  const [state, action, pending] = useActionState<StatusState, FormData>(statusAction, {});
  const app = state.application;

  return (
    <div className="space-y-6">
      <form action={action} className="grid gap-4 sm:grid-cols-[1fr_auto_auto] sm:items-end" noValidate>
        <div>
          <label htmlFor="curp" className="label">CURP</label>
          <input
            id="curp"
            name="curp"
            defaultValue={state.values?.curp}
            maxLength={18}
            spellCheck={false}
            autoCapitalize="characters"
            className="field uppercase"
            required
          />
        </div>
        <div>
          <label htmlFor="folio" className="label">Folio</label>
          <input id="folio" name="folio" defaultValue={state.values?.folio} placeholder="ASP-000123" className="field sm:w-40" required />
        </div>
        <button type="submit" disabled={pending} className="btn-primary">
          {pending ? "Buscando…" : "Consultar"}
        </button>
      </form>

      {state.error && (
        <p role="alert" className="rounded-lg bg-bad-soft px-3 py-2 text-sm text-bad">{state.error}</p>
      )}

      {app && (
        <section aria-live="polite" className="rounded-xl border border-line p-5">
          <div className="flex flex-wrap items-center justify-between gap-3">
            <h2 className="text-xl font-bold">Solicitud {app.folio}</h2>
            <span data-testid="status" className={`rounded-full px-3 py-1 text-sm font-semibold ${BADGE[app.status]}`}>
              {STATUS_INFO[app.status].label}
            </span>
          </div>
          <dl className="mt-4 grid gap-x-8 gap-y-3 sm:grid-cols-2">
            <div><dt className="text-sm text-muted">Nombre</dt><dd className="font-medium">{app.firstName} {app.lastName}</dd></div>
            <div><dt className="text-sm text-muted">Carrera</dt><dd className="font-medium">{app.careerName}</dd></div>
            <div><dt className="text-sm text-muted">Fecha de solicitud</dt><dd className="font-medium">{formatDate(app.applicationDate)}</dd></div>
            {app.enrollmentDate && (
              <div><dt className="text-sm text-muted">Fecha de inscripción</dt><dd className="font-medium">{formatDate(app.enrollmentDate)}</dd></div>
            )}
          </dl>
          <p className="mt-4 text-muted">{STATUS_INFO[app.status].detail}</p>
          {app.status === "Accepted" && (
            <Link href="/login" className="btn-primary mt-4">Ir al portal de alumnos</Link>
          )}
        </section>
      )}
    </div>
  );
}
