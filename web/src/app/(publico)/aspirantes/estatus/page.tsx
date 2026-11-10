import type { Metadata } from "next";
import Link from "next/link";
import { StatusForm } from "./status-form";

export const metadata: Metadata = { title: "Estatus de solicitud" };

export default function StatusPage() {
  return (
    <div className="mx-auto max-w-2xl px-4 py-12">
      <div className="card p-7">
        <h1 className="text-3xl font-bold text-brand-deep">Estatus de tu solicitud</h1>
        <p className="mb-6 mt-1 text-muted">Escribe tu CURP y el folio que recibiste al registrarte.</p>
        <StatusForm />
      </div>
      <p className="mt-5 text-center text-sm text-muted">
        ¿Aún no te registras?{" "}
        <Link href="/aspirantes/registro" className="font-semibold text-brand-deep hover:underline">Registro de aspirantes</Link>
      </p>
    </div>
  );
}
