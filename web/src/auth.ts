import NextAuth, { CredentialsSignin } from "next-auth";
import Credentials from "next-auth/providers/credentials";
import { authorizeStudent, type LoginFailure } from "@/lib/auth/authorize";

/** Lleva el motivo del rechazo hasta la Server Action del login (error.code). */
class LoginRejected extends CredentialsSignin {
  constructor(reason: LoginFailure) {
    super();
    this.code = reason;
  }
}

/** Rutas que exigen sesión de alumno (las comprueba el proxy y, de forma segura, la DAL). */
export const STUDENT_ROUTES = ["/dashboard", "/calificaciones", "/horario", "/perfil", "/profesores"];

export function isStudentRoute(pathname: string): boolean {
  return STUDENT_ROUTES.some((r) => pathname === r || pathname.startsWith(`${r}/`));
}

export const { handlers, auth, signIn, signOut } = NextAuth({
  session: { strategy: "jwt", maxAge: 8 * 60 * 60 },
  pages: { signIn: "/login" },
  trustHost: true,
  logger: {
    // Un login rechazado es un evento normal (contraseña incorrecta, aspirante...), no un error del servidor.
    error(error) {
      if (error instanceof CredentialsSignin) return;
      // Auth.js envuelve la falla real (p. ej. la BD no responde) en error.cause.
      // Recorre la cadena de causas (Auth.js → Drizzle → driver) para mostrar la falla real.
      const chain: string[] = [];
      let current: unknown = (error.cause as { err?: unknown } | undefined)?.err ?? error.cause;
      while (current instanceof Error && chain.length < 5) {
        const inner = current instanceof AggregateError ? current.errors.map((e) => String(e?.message ?? e)).join("; ") : "";
        chain.push(`${current.name}: ${current.message.split("\n")[0]}${inner ? ` (${inner})` : ""}`);
        current = current.cause;
      }
      console.error(`[auth] ${error.name} → ${chain.join(" → ") || String(current)}`);
    },
  },
  providers: [
    Credentials({
      credentials: { email: {}, password: {} },
      async authorize(credentials) {
        const result = await authorizeStudent(credentials);
        if (!result.ok) {
          if (result.reason === "invalid") return null; // mensaje genérico
          throw new LoginRejected(result.reason);
        }
        return {
          id: String(result.user.idUser),
          email: result.user.email,
          name: result.user.name,
          idStudent: result.user.idStudent,
        };
      },
    }),
  ],
  callbacks: {
    jwt({ token, user }) {
      if (user?.idStudent !== undefined) {
        // `user` solo llega al iniciar sesión, y authorize() siempre trae idStudent.
        token.role = "Student";
        token.idUser = Number(user.id);
        token.idStudent = user.idStudent;
      }
      return token;
    },
    session({ session, token }) {
      session.user.role = token.role;
      session.user.idUser = token.idUser;
      session.user.idStudent = token.idStudent;
      return session;
    },
    /**
     * Revisión optimista del proxy (solo lee la cookie). La verificación segura contra la
     * base la hace la DAL (lib/dal.ts) en cada página y acción.
     */
    authorized({ auth: session, request }) {
      const { pathname } = request.nextUrl;
      const isStudent = session?.user?.role === "Student";
      if (isStudentRoute(pathname)) return isStudent; // false → redirige a /login
      if (pathname === "/login" && isStudent) {
        return Response.redirect(new URL("/dashboard", request.nextUrl));
      }
      return true;
    },
  },
});
