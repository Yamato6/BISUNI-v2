import type { Metadata } from "next";
import Link from "next/link";
import { Stat } from "@/components/stat";
import { requireStudent } from "@/lib/dal";
import { formatGrade } from "@/lib/grades";
import { getGrades, getOverview } from "@/lib/student";

export const metadata: Metadata = { title: "Inicio" };

export default async function DashboardPage() {
  const { idStudent } = await requireStudent();
  const [me, grades] = await Promise.all([getOverview(idStudent), getGrades(idStudent)]);
  if (!me) return null;

  const current = grades.subjects.filter((s) => s.current);
  const info: Array<[string, string]> = [
    ["Nombre", `${me.firstName} ${me.lastName}`],
    ["Usuario", me.username],
    ["Correo", me.email],
    ["Carrera", me.careerName],
    ["Semestre", `${me.semester}°`],
    ["Grupo", me.groupName ?? "Sin grupo asignado"],
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

      {!me.groupName && (
        <p className="rounded-lg bg-warn-soft px-4 py-3 text-warn">
          Todavía no tienes grupo asignado. Control escolar te asignará uno; mientras tanto no verás horario ni materias.
        </p>
      )}

      <section aria-label="Resumen" className="grid grid-cols-2 gap-3 sm:gap-4 lg:grid-cols-4">
        <Stat label="Materias en curso" value={current.length} hint={me.groupName ? `Grupo ${me.groupName}` : undefined} />
        <Stat label="Promedio general" value={formatGrade(me.gpa)} hint="Registrado por control escolar" />
        <Stat label="Materias aprobadas" value={grades.summary.approved} hint="Final de 6 o más" />
        <Stat label="En proceso" value={grades.summary.inProgress} hint="Faltan parciales por capturar" />
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

      <section className="grid gap-4 sm:grid-cols-2">
        <Link href="/calificaciones" className="card transition hover:border-brand-dark">
          <h2 className="text-lg font-bold text-brand-deep">Mis calificaciones →</h2>
          <p className="text-sm text-muted">Parciales y calificación final de cada materia.</p>
        </Link>
        <Link href="/horario" className="card transition hover:border-brand-dark">
          <h2 className="text-lg font-bold text-brand-deep">Mi horario →</h2>
          <p className="text-sm text-muted">Tus clases de lunes a viernes.</p>
        </Link>
      </section>
    </div>
  );
}
