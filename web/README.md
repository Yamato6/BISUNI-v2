# BISUNI v2 — Portal web (`web/`)

Portal para alumnos y aspirantes. Next.js 16 (App Router) + TypeScript + Drizzle + Auth.js v5 + Tailwind 4.

## Cómo arrancarlo

Requisitos: Node 20.9 o superior y una base de datos con el esquema y el seed (ver el README de la raíz;
sin Docker: `cd ../desktop && ./mvnw exec:java`). La conexión y `AUTH_SECRET` se leen del `.env` de la
**raíz del repositorio**.

```bash
npm install
npm run dev          # http://localhost:3000
```

Cuenta de prueba: `student001@bisuni.edu` / `password123`.

> Si cambias el `.env` (puerto, contraseña…), **reinicia `npm run dev`**: Next no vuelve a leer el `.env` de la
> raíz mientras corre. Si el login dice "No se pudo conectar con la base de datos", revisa que PostgreSQL esté
> arriba y que el `.env` apunte a él.

## Scripts

| Script | Qué hace |
|---|---|
| `npm run dev` | Servidor de desarrollo |
| `npm run typecheck` | `next typegen` + `tsc --noEmit` |
| `npm run lint` | ESLint |
| `npm test` | Vitest: unitarias + integración contra PostgreSQL 17 embebido con las migraciones y el seed reales |
| `npm run test:e2e` | `next build` + Playwright contra un servidor con su propia base embebida (no toca la de desarrollo) |
| `npm run db:pull` | Regenera `src/db/generated` desde la base con `drizzle-kit pull` |

## Reglas

- El esquema **no se define aquí**: sale de `../db/migrations` (Flyway) con `npm run db:pull`.
  No usar `drizzle-kit push` ni `generate`.
- `proxy.ts` (antes `middleware.ts`) solo hace la revisión optimista de la cookie. La verificación segura
  está en `src/lib/dal.ts` (`requireStudent`), que toda página o acción del alumno debe llamar.
- `grades.final_grade` es una columna generada: se lee, nunca se escribe.
