import { Brand } from "@/components/brand";
import { requireStudent } from "@/lib/dal";
import { getOverview } from "@/lib/student";
import { logoutAction } from "./actions";
import { StudentNav } from "./student-nav";

export default async function StudentLayout({ children }: LayoutProps<"/">) {
  const { idStudent } = await requireStudent();
  const me = await getOverview(idStudent);

  return (
    <div className="flex min-h-full flex-1 flex-col md:flex-row">
      <aside className="flex flex-col gap-5 bg-gradient-to-b from-brand-dark to-brand-deep p-4 text-white md:sticky md:top-0 md:h-screen md:w-64">
        <Brand subtitle="Portal del alumno" href="/dashboard" inverted />
        <StudentNav />
        <div className="mt-auto hidden border-t border-white/20 pt-4 md:block">
          <p className="truncate font-semibold">{me ? `${me.firstName} ${me.lastName}` : ""}</p>
          <p className="truncate text-sm text-white/80">{me?.email}</p>
        </div>
        <form action={logoutAction} className="md:mt-0">
          <button type="submit" className="w-full rounded-lg border border-white/60 px-3 py-2 text-sm font-semibold hover:bg-white/10">
            Cerrar sesión
          </button>
        </form>
      </aside>
      <main className="flex-1 p-4 md:p-8">{children}</main>
    </div>
  );
}
