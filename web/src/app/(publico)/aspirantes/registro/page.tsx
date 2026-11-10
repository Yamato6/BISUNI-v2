import type { Metadata } from "next";
import { activeCareers } from "@/lib/aspirants";
import { RegistrationForm } from "./registration-form";

export const metadata: Metadata = { title: "Registro de aspirantes" };

// Lee el catálogo de carreras en cada visita (no en el build).
export const dynamic = "force-dynamic";

export default async function RegistrationPage() {
  const careers = await activeCareers();
  return (
    <div className="mx-auto max-w-2xl px-4 py-12">
      <div className="card p-7">
        <h1 className="text-3xl font-bold text-brand-deep">Registro de aspirantes</h1>
        <p className="mb-6 mt-1 text-muted">
          Llena tus datos para solicitar tu ingreso. Al terminar recibirás un folio para consultar el estatus de tu solicitud.
        </p>
        <RegistrationForm careers={careers} />
      </div>
    </div>
  );
}
