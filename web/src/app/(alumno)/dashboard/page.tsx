import type { Metadata } from "next";
import Link from "next/link";
import { Stat } from "@/components/stat";
import { getAnnouncements, getAttendance, getKardex } from "@/lib/academic";
import { RISK_PCT } from "@/lib/academic-rules";
import { requireStudent } from "@/lib/dal";
import { formatGrade } from "@/lib/grades";
import { ACADEMIC_STATUS_LABEL, getGrades, getOverview } from "@/lib/student";

export const metadata: Metadata = { title: "Inicio" };

const DATE = new Intl.DateTimeFormat("es-MX", { day: "numeric", month: "long", timeZone: "America/Mexico_City" });

export default async function DashboardPage({ searchParams }: PageProps<"/dashboard">) {
  const { idStudent } = await requireStudent();
  const { contrasena } = await searchParams;
  const [me, grades, announcements, attendance, kardex] = await Promise.all([
    getOverview(idStudent),
    getGrades(idStudent),
    getAnnouncements(idStudent),
    getAttendance(idStudent),
    getKardex(idStudent),
  ]);
  if (!me) return null;

  const current = grades.subjects.filter((s) => s.current);
  const risky = attendance.subjects.filter((s) => s.atRisk);
  const owed = kardex?.credits.owed ?? [];
  const info: Array<[string, string]> = [
    ["Nombre", `${me.firstName} ${me.lastName}`],
    ["Matrícula", me.enrollmentNumber],
    ["Situación", ACADEMIC_STATUS_LABEL[me.academicStatus]],
    ["Usuario", me.username],
    ["Correo", me.email],
    ["Carrera", me.careerName],
    ["Semestre", `${me.semester}°`],
    ["Grupo", me.groupName ? `${me.groupName} (periodo ${me.periodName})` : "Sin grupo asignado"],
    ["Tutor", me.tutorName ?? "—"],
  ];

  return (
    <div className="mx-auto max-w-5xl space-y-6">
      <header>
        <h1 className="text-3xl font-bold">¡Hola, {me.firstName}!</h1>
        <p className="text-muted">
          {me.careerName} · {me.semester}° semestre{me.groupName ? ` · Grupo ${me.groupName}` : ""}
        </p>
      </header>

      {contrasena === "nueva" && (
        <p role="status" className="rounded-lg bg-ok-soft px-4 py-3 text-ok">
          Listo: tu contraseña se cambió. Úsala la próxima vez que entres.
        </p>
      )}

      {me.academicStatus === "Graduated" && (
        <p className="rounded-lg bg-brand-soft px-4 py-3 text-brand-deep">
          ¡Felicidades, egresaste! Puedes seguir consultando tu kárdex y descargar tus documentos.
        </p>
      )}
      {me.academicStatus === "Leave" && (
        <p className="rounded-lg bg-warn-soft px-4 py-3 text-warn">
          Estás en baja temporal. Para reinscribirte acude a control escolar.
        </p>
      )}
      {me.academicStatus === "Active" && !me.groupName && (
        <p className="rounded-lg bg-warn-soft px-4 py-3 text-warn">
          Todavía no tienes grupo asignado. Control escolar te asignará uno; mientras tanto no verás horario ni materias.
        </p>
      )}

      {(risky.length > 0 || owed.length > 0) && (
        <section aria-label="Alertas" className="space-y-2">
          {risky.map((s) => (
            <p key={s.idSubject} className="rounded-lg bg-bad-soft px-4 py-3 text-bad">
              Llevas {s.absences} {s.absences === 1 ? "falta" : "faltas"} en <strong>{s.subjectName}</strong> ({s.absencePct} % de las clases). Pasar del{" "}
              {RISK_PCT} % te pone en riesgo de reprobar.{" "}
              <Link href="/asistencia" className="font-semibold underline">Ver asistencia</Link>
            </p>
          ))}
          {owed.length > 0 && (
            <p className="rounded-lg bg-bad-soft px-4 py-3 text-bad">
              Debes {owed.length === 1 ? "la materia" : "las materias"} <strong>{owed.join(", ")}</strong>. Consulta con
              tu tutor cómo acreditarla{owed.length === 1 ? "" : "s"}.{" "}
              <Link href="/kardex" className="font-semibold underline">Ver kárdex</Link>
            </p>
          )}
        </section>
      )}

      <section aria-label="Resumen" className="grid grid-cols-2 gap-3 sm:gap-4 lg:grid-cols-4">
        <Stat label="Materias en curso" value={current.length} hint={me.groupName ? `Grupo ${me.groupName}` : undefined} />
        <Stat
          label="Promedio general"
          value={formatGrade(me.gpa)}
          hint={me.gradedSubjects > 0 ? `De ${me.gradedSubjects} ${me.gradedSubjects === 1 ? "materia" : "materias"} con final` : "Aún sin calificaciones finales"}
        />
        <Stat
          label="Avance de carrera"
          value={kardex ? `${kardex.credits.percent} %` : "—"}
          hint={kardex ? `${kardex.credits.approved} de ${kardex.credits.total} créditos` : undefined}
        />
        <Stat
          label="Faltas en el periodo"
          value={attendance.subjects.reduce((a, s) => a + s.absences, 0)}
          hint={attendance.subjects.length > 0 ? "En todas tus materias" : "Sin registros"}
        />
      </section>

      <section className="card" aria-labelledby="avisos">
        <h2 id="avisos" className="text-xl font-bold">Avisos</h2>
        {announcements.length === 0 ? (
          <p className="mt-2 text-muted">No hay avisos por ahora.</p>
        ) : (
          <ul className="mt-3 divide-y divide-line">
            {announcements.map((a) => (
              <li key={a.id} className="py-3" data-testid="aviso">
                <p className="font-semibold">{a.title}</p>
                <p className="mt-1 whitespace-pre-line text-sm">{a.body}</p>
                <p className="mt-1 text-xs text-muted">
                  {a.scope} · {a.authorName} · {DATE.format(new Date(a.publishedAt))}
                </p>
              </li>
            ))}
          </ul>
        )}
      </section>

      <section className="card">
        <h2 className="text-xl font-bold">Información personal</h2>
        <dl className="mt-4 grid gap-x-8 gap-y-3 sm:grid-cols-2">
          {info.map(([k, v]) => (
            <div key={k}>
              <dt className="text-sm text-muted">{k}</dt>
              <dd className="font-medium">{v}</dd>
            </div>
          ))}
        </dl>
      </section>

      <section className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        <Link href="/calificaciones" className="card transition hover:border-brand-dark">
          <h2 className="text-lg font-bold text-brand-deep">Mis calificaciones →</h2>
          <p className="text-sm text-muted">Parciales y calificación final del periodo.</p>
        </Link>
        <Link href="/kardex" className="card transition hover:border-brand-dark">
          <h2 className="text-lg font-bold text-brand-deep">Kárdex →</h2>
          <p className="text-sm text-muted">Tu historial completo y avance de créditos.</p>
        </Link>
        <Link href="/documentos" className="card transition hover:border-brand-dark">
          <h2 className="text-lg font-bold text-brand-deep">Documentos →</h2>
          <p className="text-sm text-muted">Boleta, kárdex y constancia de estudios en PDF.</p>
        </Link>
      </section>
    </div>
  );
}
