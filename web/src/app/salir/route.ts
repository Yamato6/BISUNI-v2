import { signOut } from "@/auth";

/**
 * Cierra la sesión cuando la DAL detecta que la cuenta ya no es válida (desactivada, ya no es
 * alumno...). Un render no puede borrar cookies; un Route Handler sí.
 */
export async function GET(request: Request) {
  const motivo = new URL(request.url).searchParams.get("motivo");
  await signOut({ redirectTo: motivo === "expirada" ? "/login?sesion=expirada" : "/login" });
}
