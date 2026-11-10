import type { Metadata } from "next";
import Link from "next/link";
import { LoginForm } from "./login-form";

export const metadata: Metadata = { title: "Portal de alumnos" };

export default async function LoginPage({ searchParams }: PageProps<"/login">) {
  const { sesion } = await searchParams;
  const notice = sesion === "expirada" ? "Tu sesión ya no es válida. Vuelve a iniciar sesión." : undefined;

  return (
    <div className="mx-auto flex max-w-md flex-col px-4 py-14">
      <div className="card p-7">
        <h1 className="text-3xl font-bold text-brand-deep">Portal de alumnos</h1>
        <p className="mb-6 mt-1 text-muted">Entra con tu correo institucional.</p>
        <LoginForm notice={notice} />
      </div>
      <p className="mt-5 text-center text-sm text-muted">
        ¿Eres aspirante?{" "}
        <Link href="/aspirantes/estatus" className="font-semibold text-brand-deep hover:underline">
          Consulta el estatus de tu solicitud
        </Link>
      </p>
    </div>
  );
}
