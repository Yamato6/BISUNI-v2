"use server";

import { AuthError, CredentialsSignin } from "next-auth";
import { signIn } from "@/auth";
import { LOGIN_MESSAGES, loginSchema, type LoginFailure } from "@/lib/auth/authorize";

export interface LoginState {
  error?: string;
  email?: string;
}

export async function loginAction(_prev: LoginState, formData: FormData): Promise<LoginState> {
  const email = String(formData.get("email") ?? "");
  const parsed = loginSchema.safeParse({ email, password: formData.get("password") ?? "" });
  if (!parsed.success) {
    return { error: parsed.error.issues[0]?.message ?? LOGIN_MESSAGES.invalid, email };
  }
  try {
    // En éxito, signIn lanza la redirección de Next (NEXT_REDIRECT); no se debe atrapar.
    await signIn("credentials", { ...parsed.data, redirectTo: "/dashboard" });
    return {};
  } catch (error) {
    if (error instanceof CredentialsSignin) {
      const code = error.code as LoginFailure;
      return { error: LOGIN_MESSAGES[code] ?? LOGIN_MESSAGES.invalid, email };
    }
    if (error instanceof AuthError) {
      const message = isDatabaseDown(error)
        ? "No se pudo conectar con la base de datos. Intenta más tarde o avisa a control escolar."
        : "No se pudo iniciar sesión. Intenta de nuevo.";
      return { error: message, email };
    }
    throw error;
  }
}

/** ¿La falla viene de que la base de datos no acepta conexiones? (recorre error.cause) */
function isDatabaseDown(error: unknown): boolean {
  let current: unknown = error;
  for (let i = 0; i < 6 && current; i++) {
    const e = current as { code?: string; errors?: Array<{ code?: string }>; cause?: unknown; err?: unknown };
    if (e.code === "ECONNREFUSED" || e.errors?.some((x) => x?.code === "ECONNREFUSED")) return true;
    current = e.err ?? e.cause;
  }
  return false;
}
