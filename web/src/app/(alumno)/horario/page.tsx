import type { Metadata } from "next";
import { requireStudent } from "@/lib/dal";
import { WEEKDAYS, WEEKDAY_LABEL, buildScheduleGrid, hhmm, subjectColor } from "@/lib/schedule";
import { getSchedule } from "@/lib/student";

export const metadata: Metadata = { title: "Horario" };

export default async function SchedulePage() {
  const { idStudent } = await requireStudent();
  const schedule = await getSchedule(idStudent);
  const grid = buildScheduleGrid(schedule.slots, schedule.classes);

  return (
    <div className="mx-auto max-w-6xl space-y-6">
      <header>
        <h1 className="text-3xl font-bold">Mi horario</h1>
        <p className="text-muted">
          {schedule.groupName
            ? `Grupo ${schedule.groupName} · ${schedule.classes.length} clases por semana`
            : "Todavía no tienes grupo asignado."}
        </p>
      </header>

      {schedule.groupName && (
        <section className="card overflow-x-auto p-3 sm:p-5">
          <table className="w-full min-w-[760px] table-fixed border-separate border-spacing-1 text-sm">
            <caption className="sr-only">Horario semanal del grupo {schedule.groupName}</caption>
            <thead>
              <tr>
                <th scope="col" className="w-28 text-left text-xs font-semibold text-muted">Hora</th>
                {WEEKDAYS.map((d) => (
                  <th key={d} scope="col" className="rounded-md bg-brand-soft py-2 font-semibold text-brand-deep">
                    {WEEKDAY_LABEL[d]}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {grid.map((row) => (
                <tr key={row.slot.idSlot}>
                  <th scope="row" className="pr-2 text-left text-xs font-medium text-muted">
                    {hhmm(row.slot.startTime)} – {hhmm(row.slot.endTime)}
                  </th>
                  {row.cells.map((c, i) =>
                    c ? (
                      <td
                        key={i}
                        className="h-16 rounded-md px-2 py-1.5 text-center align-middle"
                        style={{ backgroundColor: subjectColor(c.idSubject) }}
                      >
                        <span className="block font-semibold leading-tight text-ink">{c.subjectName}</span>
                        <span className="block text-xs text-ink/70">{c.teacherName}</span>
                      </td>
                    ) : (
                      <td key={i} className="h-16 rounded-md bg-canvas" aria-label="Libre" />
                    ),
                  )}
                </tr>
              ))}
            </tbody>
          </table>
        </section>
      )}
    </div>
  );
}
