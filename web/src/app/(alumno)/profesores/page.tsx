import type { Metadata } from "next";
import { requireStudent } from "@/lib/dal";
import { getTeachers } from "@/lib/student";

export const metadata: Metadata = { title: "Mis profesores" };

export default async function TeachersPage() {
  const { idStudent } = await requireStudent();
  const { groupName, teachers } = await getTeachers(idStudent);

  return (
    <div className="mx-auto max-w-5xl space-y-6">
      <header>
        <h1 className="text-3xl font-bold">Mis profesores</h1>
        <p className="text-muted">
          {groupName ? `Quienes te dan clase en el grupo ${groupName}.` : "Todavía no tienes grupo asignado."}
        </p>
      </header>

      {groupName && teachers.length === 0 && (
        <p className="card text-muted">Tu grupo todavía no tiene profesores asignados.</p>
      )}

      <ul className="grid gap-4 md:grid-cols-2">
        {teachers.map((t) => (
          <li key={t.idTeacher} className="card space-y-3">
            <div className="flex flex-wrap items-start justify-between gap-2">
              <div>
                <h2 className="text-xl font-bold">{t.name}</h2>
                {t.specialty && <p className="text-sm text-muted">{t.specialty}</p>}
              </div>
              {t.tutor && (
                <span className="rounded-full bg-brand-soft px-2.5 py-1 text-xs font-semibold text-brand-deep">Tutor del grupo</span>
              )}
            </div>
            {t.subjects.length > 0 && (
              <div>
                <p className="text-sm text-muted">Te imparte</p>
                <ul className="mt-1 flex flex-wrap gap-2">
                  {t.subjects.map((sub) => (
                    <li key={sub} className="rounded-md bg-canvas px-2 py-1 text-sm font-medium">{sub}</li>
                  ))}
                </ul>
              </div>
            )}
            <dl className="space-y-1 text-sm">
              <div className="flex gap-2">
                <dt className="text-muted">Correo:</dt>
                <dd><a href={`mailto:${t.email}`} className="font-medium text-brand-deep hover:underline">{t.email}</a></dd>
              </div>
              {t.phone && (
                <div className="flex gap-2">
                  <dt className="text-muted">Teléfono:</dt>
                  <dd className="font-medium">{t.phone}</dd>
                </div>
              )}
            </dl>
          </li>
        ))}
      </ul>
    </div>
  );
}
