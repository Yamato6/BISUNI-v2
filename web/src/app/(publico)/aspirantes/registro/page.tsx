import type { Metadata } from "next";
import Link from "next/link";
import { activeCareers, openAdmissionPeriod } from "@/lib/aspirants";
import { RegistrationForm } from "./registration-form";

export const metadata: Metadata = { title: "Registro de aspirantes" };

// Lee el catálogo de carreras en cada visita (no en el build).
export const dynamic = "force-dynamic";

export default async function RegistrationPage() {
  const [careers, period] = await Promise.all([activeCareers(), openAdmissionPeriod()]);
  return (
    <div className="mx-auto max-w-2xl px-4 py-12">
      <div className="card p-7">
        <h1 className="text-3xl font-bold text-brand-deep">Registro de aspirantes</h1>
        {period ? (
          <>
            <p className="mb-6 mt-1 text-muted">
              Convocatoria <strong data-testid="convocatoria">{period.name}</strong>. Llena tus datos para solicitar tu
              ingreso. Al terminar recibirás un folio para consultar el estatus de tu solicitud.
            </p>
            <RegistrationForm careers={careers} />
          </>
        ) : (
          <div className="mt-4 space-y-3" role="status">
            <p className="text-muted">
              La convocatoria de admisión está cerrada por ahora. Consulta las fechas de la siguiente convocatoria con
              control escolar.
            </p>
            <p>
              ¿Ya enviaste tu solicitud?{" "}
              <Link href="/aspirantes/estatus" className="font-medium text-brand underline">
                Consulta su estatus
              </Link>
              .
            </p>
          </div>
        )}
      </div>
    </div>
  );
}
