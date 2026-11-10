/**
 * Proxy (antes "middleware" en Next 15): revisión optimista de la sesión en cada ruta.
 * Solo lee la cookie (callback `authorized` de src/auth.ts). La verificación segura contra la
 * base de datos está en la DAL (src/lib/dal.ts), junto a cada consulta.
 */
export { auth as proxy } from "@/auth";

export const config = {
  matcher: ["/((?!api|_next/static|_next/image|favicon.ico|bis.png).*)"],
};
