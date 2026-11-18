import type { Metadata } from "next";
import { getKardex } from "@/lib/academic";
import { requireStudent } from "@/lib/dal";

export const metadata: Metadata = { title: "Documentos" };

/*
 * Los PDF los generan Route Handlers (documentos/constancia, kardex y boleta). Los enlaces son <a>
 * normales: una descarga no es una navegación del App Router.
 */
export default async function DocumentsPage() {
  const { idStudent, academicStatus } = await requireStudent();
  const kardex = await getKardex(idStudent);
  const periods = [...(kardex?.periods ?? [])].reverse();

  return (
    <div className="mx-auto max-w-5xl space-y-6">
      <header>
        <h1 className="text-3xl font-bold">Documentos</h1>
        <p className="text-muted">
          Descarga tus documentos en PDF. Cada uno lleva un folio y la fecha en que se generó; para trámites oficiales
          con firma, acude a control escolar.
        </p>
      </header>

      <div className="grid gap-4 md:grid-cols-3">
        <section className="card flex flex-col">
          <h2 className="text-lg font-bold">Constancia de estudios</h2>
          <p className="mt-1 flex-1 text-sm text-muted">
            {academicStatus === "Graduated"
              ? "Hace constar que egresaste de tu carrera."
              : "Hace constar que estás inscrito, con tu semestre, grupo y periodo."}
          </p>
          {academicStatus === "Leave" ? (
            <p className="mt-4 text-sm text-warn">En baja temporal la constancia la expide control escolar.</p>
          ) : (
            <a href="/documentos/constancia" className="btn-primary mt-4 text-center" download data-testid="constancia">
              Descargar constancia
            </a>
          )}
        </section>

        <section className="card flex flex-col">
          <h2 className="text-lg font-bold">Kárdex</h2>
          <p className="mt-1 flex-1 text-sm text-muted">Todas tus materias por periodo, promedio y avance de créditos.</p>
          <a href="/documentos/kardex" className="btn-primary mt-4 text-center" download>
            Descargar kárdex
          </a>
        </section>

        <section className="card flex flex-col">
          <h2 className="text-lg font-bold">Boleta de calificaciones</h2>
          <p className="mt-1 text-sm text-muted">Parciales y finales de un periodo.</p>
          {periods.length === 0 ? (
            <p className="mt-4 text-sm text-muted">Aún no tienes calificaciones.</p>
          ) : (
            <ul className="mt-4 space-y-2">
              {periods.map((p) => (
                <li key={p.idPeriod}>
                  <a href={`/documentos/boleta?periodo=${p.idPeriod}`} className="btn-secondary block text-center" download>
                    Boleta {p.name}
                    {p.status === "Active" ? " (en curso)" : ""}
                  </a>
                </li>
              ))}
            </ul>
          )}
        </section>
      </div>
    </div>
  );
}
