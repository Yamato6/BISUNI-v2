import type { Metadata } from "next";
import { getAttendance } from "@/lib/academic";
import { RISK_PCT } from "@/lib/academic-rules";
import { requireStudent } from "@/lib/dal";

export const metadata: Metadata = { title: "Asistencia" };

const DATE = new Intl.DateTimeFormat("es-MX", { weekday: "short", day: "numeric", month: "short", timeZone: "UTC" });
const INCIDENT = {
  Absent: { label: "Falta", className: "bg-bad-soft text-bad" },
  Late: { label: "Retardo", className: "bg-warn-soft text-warn" },
  Excused: { label: "Justificada", className: "bg-brand-soft text-brand-deep" },
} as const;

export default async function AttendancePage() {
  const { idStudent } = await requireStudent();
  const { groupName, subjects } = await getAttendance(idStudent);

  return (
    <div className="mx-auto max-w-5xl space-y-6">
      <header>
        <h1 className="text-3xl font-bold">Asistencia</h1>
        <p className="text-muted">
          {groupName ? `Grupo ${groupName}. ` : ""}Faltas por materia en el periodo. Los retardos y las faltas justificadas no
          cuentan como falta; pasar del {RISK_PCT} % de faltas te pone en riesgo de reprobar.
        </p>
      </header>

      {subjects.length === 0 && (
        <p className="card text-muted">No tienes materias en curso, así que no hay asistencia que mostrar.</p>
      )}

      <div className="grid gap-4 md:grid-cols-2">
        {subjects.map((s) => (
          <section key={s.idSubject} className={`card ${s.atRisk ? "border-bad" : ""}`} aria-labelledby={`m-${s.idSubject}`}>
            <h2 id={`m-${s.idSubject}`} className="text-lg font-bold">{s.subjectName}</h2>
            <p className="text-sm text-muted">{s.teacherName ?? "Profesor por asignar"}</p>
            {s.sessions === 0 ? (
              <p className="mt-3 text-muted">El profesor todavía no ha pasado lista.</p>
            ) : (
              <>
                <p className="mt-3">
                  <span className={`font-display text-3xl font-bold ${s.atRisk ? "text-bad" : "text-ink"}`}>{s.absencePct} %</span>{" "}
                  <span className="text-sm text-muted">de faltas</span>
                </p>
                <p className="text-sm">
                  {s.absences} {s.absences === 1 ? "falta" : "faltas"} de {s.sessions} clases · {s.lates}{" "}
                  {s.lates === 1 ? "retardo" : "retardos"} · {s.excused} {s.excused === 1 ? "justificada" : "justificadas"}
                </p>
                {s.atRisk && (
                  <p className="mt-2 rounded-lg bg-bad-soft px-3 py-2 text-sm text-bad">En riesgo por faltas: habla con tu profesor o tutor.</p>
                )}
                {s.incidents.length > 0 && (
                  <details className="mt-3 text-sm">
                    <summary className="cursor-pointer font-medium text-brand-deep">Ver fechas ({s.incidents.length})</summary>
                    <ul className="mt-2 flex flex-wrap gap-2">
                      {s.incidents.map((i) => (
                        <li key={`${i.date}-${i.status}`} className={`rounded-full px-2.5 py-1 text-xs font-semibold ${INCIDENT[i.status].className}`}>
                          {DATE.format(new Date(`${i.date}T00:00:00Z`))} · {INCIDENT[i.status].label}
                        </li>
                      ))}
                    </ul>
                  </details>
                )}
              </>
            )}
          </section>
        ))}
      </div>
    </div>
  );
}
