import type { Metadata } from "next";
import { requireStudent } from "@/lib/dal";
import { getOverview } from "@/lib/student";
import { NameForm, PasswordForm } from "./profile-forms";

export const metadata: Metadata = { title: "Mi perfil" };

export default async function ProfilePage() {
  const { idStudent } = await requireStudent();
  const me = await getOverview(idStudent);
  if (!me) return null;

  const academic: Array<[string, string]> = [
    ["Correo (usuario)", me.email],
    ["Carrera", me.careerName],
    ["Semestre", `${me.semester}°`],
    ["Grupo", me.groupName ?? "Sin grupo asignado"],
  ];

  return (
    <div className="mx-auto max-w-3xl space-y-6">
      <header>
        <h1 className="text-3xl font-bold">Mi perfil</h1>
        <p className="text-muted">Actualiza tus datos y tu contraseña.</p>
      </header>

      <section className="card">
        <h2 className="text-xl font-bold">Datos académicos</h2>
        <dl className="mt-4 grid gap-x-8 gap-y-3 sm:grid-cols-2">
          {academic.map(([k, v]) => (
            <div key={k}>
              <dt className="text-sm text-muted">{k}</dt>
              <dd className="font-medium">{v}</dd>
            </div>
          ))}
        </dl>
        <p className="mt-4 text-sm text-muted">
          Estos datos los administra control escolar. Si alguno es incorrecto, acude a sus oficinas.
        </p>
      </section>

      <section className="card space-y-4">
        <h2 className="text-xl font-bold">Datos personales</h2>
        <NameForm firstName={me.firstName} lastName={me.lastName} />
      </section>

      <section className="card space-y-4">
        <h2 className="text-xl font-bold">Cambiar contraseña</h2>
        <PasswordForm />
      </section>
    </div>
  );
}
