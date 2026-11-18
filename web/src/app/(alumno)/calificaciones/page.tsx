import type { Metadata } from "next";
import Link from "next/link";
import { Stat } from "@/components/stat";
import { requireStudent } from "@/lib/dal";
import { STATUS_LABEL, formatGrade, gradeLevel, gradeStatus, type GradeStatus } from "@/lib/grades";
import { getGrades, type SubjectGrade } from "@/lib/student";

export const metadata: Metadata = { title: "Calificaciones" };

const LEVEL_CLASS = {
  alta: "text-ok",
  media: "text-warn",
  baja: "text-bad",
  vacia: "text-muted",
} as const;

const STATUS_CLASS: Record<GradeStatus, string> = {
  aprobada: "bg-ok-soft text-ok",
  reprobada: "bg-bad-soft text-bad",
  "en-proceso": "bg-warn-soft text-warn",
};

function Grade({ value, strong = false }: { value: number | null; strong?: boolean }) {
  return <span className={`${LEVEL_CLASS[gradeLevel(value)]} ${strong ? "font-bold" : ""}`}>{formatGrade(value)}</span>;
}

function GradesTable({ rows }: { rows: SubjectGrade[] }) {
  return (
    <div className="overflow-x-auto">
      <table className="w-full min-w-[720px] text-left text-sm">
        <thead className="border-b border-line text-muted">
          <tr>
            <th scope="col" className="py-2 pr-4 font-semibold">Materia</th>
            <th scope="col" className="py-2 pr-4 font-semibold">Profesor</th>
            <th scope="col" className="py-2 pr-4 text-center font-semibold">Parcial 1</th>
            <th scope="col" className="py-2 pr-4 text-center font-semibold">Parcial 2</th>
            <th scope="col" className="py-2 pr-4 text-center font-semibold">Parcial 3</th>
            <th scope="col" className="py-2 pr-4 text-center font-semibold">Final</th>
            <th scope="col" className="py-2 font-semibold">Estatus</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-line">
          {rows.map((r) => {
            const status = gradeStatus(r.finalGrade);
            return (
              <tr key={`${r.idGroup}-${r.idSubject}`}>
                <td className="py-3 pr-4 font-medium">
                  {r.subjectName}
                  <span className="block text-xs text-muted">{r.credits} créditos</span>
                </td>
                <td className="py-3 pr-4">{r.teacherName ?? "Por asignar"}</td>
                <td className="py-3 pr-4 text-center"><Grade value={r.parcial1} /></td>
                <td className="py-3 pr-4 text-center"><Grade value={r.parcial2} /></td>
                <td className="py-3 pr-4 text-center"><Grade value={r.parcial3} /></td>
                <td className="py-3 pr-4 text-center"><Grade value={r.finalGrade} strong /></td>
                <td className="py-3">
                  <span className={`rounded-full px-2.5 py-1 text-xs font-semibold ${STATUS_CLASS[status]}`}>
                    {STATUS_LABEL[status]}
                  </span>
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}

export default async function GradesPage() {
  const { idStudent } = await requireStudent();
  const { subjects, summary } = await getGrades(idStudent);
  const current = subjects.filter((s) => s.current);
  const previous = subjects.filter((s) => !s.current);
  const previousGroups = [...new Set(previous.map((s) => s.groupName))];

  return (
    <div className="mx-auto max-w-5xl space-y-6">
      <header>
        <h1 className="text-3xl font-bold">Mis calificaciones</h1>
        <p className="text-muted">La calificación final es el promedio de los tres parciales. Se aprueba con 6 o más.</p>
      </header>

      <section aria-label="Resumen" className="grid grid-cols-2 gap-3 sm:gap-4 lg:grid-cols-4">
        <Stat label="Promedio de finales" value={formatGrade(summary.average)} hint="Solo materias con final" />
        <Stat label="Materias" value={summary.total} />
        <Stat label="Aprobadas" value={summary.approved} />
        <Stat label="En proceso" value={summary.inProgress} />
      </section>

      <section className="card">
        <h2 className="mb-3 text-xl font-bold">Grupo actual{current[0] ? ` · ${current[0].groupName}` : ""}</h2>
        {current.length > 0 ? (
          <GradesTable rows={current} />
        ) : (
          <p className="text-muted">No tienes materias en curso. Si no tienes grupo asignado, acude a control escolar.</p>
        )}
      </section>

      {previous.length > 0 && (
        <section className="card">
          <h2 className="mb-3 text-xl font-bold">Grupos anteriores · {previousGroups.join(", ")}</h2>
          <GradesTable rows={previous} />
          <p className="mt-3 text-sm text-muted">
            Tu historial completo, por periodo y con tu avance de créditos, está en el{" "}
            <Link href="/kardex" className="font-semibold text-brand-deep hover:underline">Kárdex</Link>.
          </p>
        </section>
      )}
    </div>
  );
}
