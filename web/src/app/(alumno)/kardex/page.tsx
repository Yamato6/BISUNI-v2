import type { Metadata } from "next";
import Link from "next/link";
import { Stat } from "@/components/stat";
import { getKardex } from "@/lib/academic";
import { RESULT_LABEL, type KardexResult } from "@/lib/academic-rules";
import { requireStudent } from "@/lib/dal";
import { formatGrade, gradeLevel } from "@/lib/grades";

export const metadata: Metadata = { title: "Kárdex" };

const RESULT_CLASS: Record<KardexResult, string> = {
  Passed: "bg-ok-soft text-ok",
  Failed: "bg-bad-soft text-bad",
  InProgress: "bg-warn-soft text-warn",
};

const LEVEL_CLASS = { alta: "text-ok", media: "text-warn", baja: "text-bad", vacia: "text-muted" } as const;

export default async function KardexPage() {
  const { idStudent } = await requireStudent();
  const kardex = await getKardex(idStudent);
  if (!kardex) return null;
  const { periods, credits, gpa } = kardex;

  return (
    <div className="mx-auto max-w-5xl space-y-6">
      <header className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h1 className="text-3xl font-bold">Kárdex</h1>
          <p className="text-muted">Todas las materias que has cursado, por periodo. Se aprueba con 6 o más.</p>
        </div>
        <a href="/documentos/kardex" className="btn-primary" download>
          Descargar PDF
        </a>
      </header>

      <section aria-label="Resumen" className="grid grid-cols-2 gap-3 sm:gap-4 lg:grid-cols-4">
        <Stat label="Promedio general" value={formatGrade(gpa)} hint="De todas tus finales" />
        <Stat label="Créditos aprobados" value={`${credits.approved} de ${credits.total}`} />
        <Stat label="Avance de carrera" value={`${credits.percent} %`} />
        <Stat label="Materias adeudadas" value={credits.owed.length} hint={credits.owed.length ? credits.owed.join(", ") : "Ninguna"} />
      </section>

      <section className="card" aria-label="Avance de carrera">
        <div className="flex justify-between text-sm">
          <span className="font-semibold">Avance de carrera</span>
          <span className="text-muted">{credits.approved} de {credits.total} créditos</span>
        </div>
        <div
          className="mt-2 h-3 overflow-hidden rounded-full bg-line"
          role="progressbar"
          aria-valuenow={credits.percent}
          aria-valuemin={0}
          aria-valuemax={100}
          aria-label="Créditos aprobados"
        >
          <div className="h-full rounded-full bg-brand-dark" style={{ width: `${credits.percent}%` }} />
        </div>
      </section>

      {periods.length === 0 && (
        <p className="card text-muted">Todavía no tienes materias en tu kárdex. Aparecerán cuando tengas grupo asignado.</p>
      )}

      {[...periods].reverse().map((p) => (
        <section key={p.idPeriod} className="card" aria-labelledby={`periodo-${p.idPeriod}`}>
          <div className="mb-3 flex flex-wrap items-baseline justify-between gap-2">
            <h2 id={`periodo-${p.idPeriod}`} className="text-xl font-bold">
              Periodo {p.name} · {p.groups.join(", ")}
              {p.status === "Active" && <span className="ml-2 text-sm font-medium text-muted">(en curso)</span>}
            </h2>
            <p className="text-sm text-muted">
              Promedio del periodo: <strong className="text-ink">{formatGrade(p.average)}</strong> · {p.creditsPassed} créditos aprobados
            </p>
          </div>
          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm">
              <thead className="border-b border-line text-muted">
                <tr>
                  <th scope="col" className="py-2 pr-4 font-semibold">Materia</th>
                  <th scope="col" className="hidden py-2 pr-4 text-center font-semibold sm:table-cell">Sem.</th>
                  <th scope="col" className="py-2 pr-4 text-center font-semibold">Créd.</th>
                  <th scope="col" className="py-2 pr-4 text-center font-semibold">Final</th>
                  <th scope="col" className="py-2 font-semibold">Resultado</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-line">
                {p.entries.map((e) => (
                  <tr key={`${e.groupName}-${e.idSubject}`}>
                    <td className="py-3 pr-4 font-medium">{e.subjectName}</td>
                    <td className="hidden py-3 pr-4 text-center sm:table-cell">{e.subjectSemester}°</td>
                    <td className="py-3 pr-4 text-center">{e.credits}</td>
                    <td className={`py-3 pr-4 text-center font-bold ${LEVEL_CLASS[gradeLevel(e.finalGrade)]}`}>
                      {formatGrade(e.finalGrade)}
                    </td>
                    <td className="py-3">
                      <span className={`rounded-full px-2.5 py-1 text-xs font-semibold ${RESULT_CLASS[e.result]}`}>
                        {RESULT_LABEL[e.result]}
                      </span>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </section>
      ))}

      <p className="text-sm text-muted">
        Las materias del periodo en curso son definitivas cuando el profesor cierra el acta. Los parciales del periodo
        actual están en <Link href="/calificaciones" className="font-semibold text-brand-deep hover:underline">Calificaciones</Link>.
      </p>
    </div>
  );
}
