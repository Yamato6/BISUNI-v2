import type { Metadata } from "next";
import { redirect } from "next/navigation";
import { Brand } from "@/components/brand";
import { requireStudentForPasswordChange } from "@/lib/dal";
import { NewPasswordForm } from "./new-password-form";

export const metadata: Metadata = { title: "Crea tu contraseña" };

/**
 * Cambio obligatorio después de que control escolar restableció la contraseña. Vive fuera del
 * layout del alumno: ese layout llama a requireStudent(), que redirige aquí mientras la
 * contraseña siga siendo temporal.
 */
export default async function NewPasswordPage() {
  const { mustChangePassword } = await requireStudentForPasswordChange();
  if (!mustChangePassword) redirect("/dashboard");

  return (
    <div className="mx-auto flex max-w-md flex-col px-4 py-14">
      <div className="mb-6 flex justify-center">
        <Brand subtitle="Portal del alumno" href="/cambiar-contrasena" />
      </div>
      <div className="card p-7">
        <h1 className="text-2xl font-bold text-brand-deep">Crea tu contraseña</h1>
        <p className="mb-6 mt-1 text-muted">
          Entraste con una contraseña temporal que te dio control escolar. Antes de continuar, crea una propia.
        </p>
        <NewPasswordForm />
      </div>
      <p className="mt-5 text-center text-sm text-muted">
        <a href="/salir" className="font-semibold text-brand-deep hover:underline">Cerrar sesión</a>
      </p>
    </div>
  );
}
