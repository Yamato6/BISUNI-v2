# Guía de desarrollo

## Requisitos
- **Java 21 o superior.** Maven no hace falta: se usa el wrapper `desktop/mvnw`.
- **Node.js 20.9 o superior.** El CI usa Node 26.
- **Una base PostgreSQL** solo para usar las apps a mano. Las pruebas levantan la suya.

```bash
cp .env.example .env             # DB_PASSWORD, DATABASE_URL y AUTH_SECRET
cd desktop && ./mvnw exec:java   # base embebida con esquema y datos de demostración (déjala corriendo)
cd desktop && ./mvnw javafx:run  # escritorio
cd web && npm install && npm run dev   # portal en http://localhost:3000
```

Las otras opciones de base de datos (Docker o PostgreSQL instalado) están en [instalacion.md](instalacion.md).

## Estructura
```
db/
  migrations/V1__schema.sql     esquema (única fuente de verdad)
  seed/R__seed_dev.sql          datos de demostración (repeatable; empieza con TRUNCATE)
  local-setup.sql               usuario y base en un PostgreSQL instalado
  primer-admin.sql              primer administrador de una instalación real
desktop/                        Java 21, JavaFX 21, JDBC + HikariCP, AtlantaFX
  src/main/java/mx/bisuni/desktop/{config,db,model,dao,service,session,ui}
  src/test/java/...             pruebas + herramientas de desarrollo (DevDatabase, DbMigrate, DesktopCli)
  src/packaging/                ícono y archivos del paquete de Windows (jpackage)
web/                            Next.js 16, Drizzle, Auth.js v5, Tailwind 4
  src/app/(publico)             portada, login, registro y estatus de aspirantes
  src/app/(alumno)              portal del alumno
  src/app/cambiar-contrasena    cambio obligatorio tras una contraseña temporal
  src/lib                       reglas, consultas, PDF y la DAL (requireStudent)
  src/db/generated              esquema generado con db:pull (no se edita)
  e2e/                          pruebas Playwright
docs/                           documentación
```

## Comandos

### Escritorio (`desktop/`)
| Comando | Qué hace |
|---|---|
| `./mvnw verify` | Todas las pruebas |
| `./mvnw javafx:run` | Abre la app |
| `./mvnw exec:java` | Base embebida de desarrollo con esquema y seed (`-Dexec.args=--reset` la recrea) |
| `./mvnw exec:java "-Dexec.mainClass=mx.bisuni.desktop.dev.DbMigrate"` | Migraciones y seed en la base del `.env`. Con `--sin-seed` aplica solo las migraciones; con `--reset` borra el esquema y lo vuelve a crear |
| `./mvnw -Pdist -DskipTests clean package` | App empaquetada para Windows con jpackage, en `target/dist/BISUNI` |

### Web (`web/`)
| Comando | Qué hace |
|---|---|
| `npm run dev` | Servidor de desarrollo |
| `npm run typecheck` | `next typegen` + `tsc --noEmit` |
| `npm run lint` | ESLint |
| `npm test` | Vitest: reglas y consultas contra PostgreSQL embebido |
| `npm run test:e2e` | `next build` + Playwright, con su propio servidor y su propia base embebida |
| `npm run db:pull -- --embebida` | Regenera `src/db/generated` desde un PostgreSQL temporal con las migraciones del repo |

## Pruebas
| Suite | Comando | Qué cubre |
|---|---|---|
| Escritorio | `./mvnw verify` | Servicios contra la base real (triggers, bitácora, periodos, actas, asistencia, admisión…) y humo de cada vista |
| Web | `npm run typecheck && npm run lint && npm test` | Reglas, login, kárdex, asistencia, avisos y PDF contra la base real |
| E2E | `npm run test:e2e` | Flujos en el navegador, incluido el **flujo completo entre las dos apps** |

- Cada suite levanta su propio PostgreSQL 17 embebido con las migraciones y el seed reales. Ninguna toca tu base.
- El flujo completo (`web/e2e/flujo-completo.spec.ts`) ejecuta acciones del escritorio con sus servicios reales a
  través de `desktop/.../dev/DesktopCli.java` (vía `mvnw`), así que necesita Java.
- El CI (`.github/workflows/ci.yml`) corre todo en cada push y pull request. Las vistas de JavaFX usan una
  pantalla virtual (`xvfb`).

> **Windows:** corre las pruebas web desde una terminal cuya ruta empiece con `C:\` en mayúscula. Si queda como
> `c:\` (pasa al abrirla desde algunos editores), Vitest carga su módulo dos veces y falla con
> "Vitest failed to find the runner".

## Convenciones

### Base de datos
- **Nunca edites una migración ya publicada.** Agrega `db/migrations/V2__descripcion.sql`.
- Si agregas restricciones, pon su mensaje en `desktop/.../service/SqlErrors.java`, por el nombre de la
  restricción.
- Los triggers lanzan sus errores con `RAISE ... USING CONSTRAINT = 'nombre'` para que se traduzcan igual.
- Después de una migración, corre `npm run db:pull -- --embebida` en `web/`.

### Escritorio
- Las vistas nuevas implementan `ShellController.ContentView`, se registran en el menú de `ShellController` y se
  agregan a `ViewsSmokeTest`.
- Los formularios usan `FormDialog`; las listas, `ListSupport`.
- Las consultas a la base van con `Async`, fuera del hilo de JavaFX.
- Los servicios nuevos se conectan en `AppContext.wire`.
- Toda escritura usa `db.actor(session.idUser())`, para que la bitácora sepa quién la hizo.
- Las reglas de negocio dentro de `inTransaction` lanzan `Rejected`.

### Web
- **Next.js 16** cambió varias APIs: `middleware` ahora es `proxy`, y `params`, `searchParams` y `cookies()` son
  asíncronos. Antes de escribir código, consulta la guía en `web/node_modules/next/dist/docs/`.
- Toda página, Server Action o Route Handler del alumno llama a `requireStudent()`.
- Las consultas viven en `src/lib` con `server-only`. Las reglas puras van en archivos `*-rules.ts`, con sus
  pruebas unitarias.

### General
- Interfaz, mensajes y documentación en español. Código (clases, tablas, columnas) en inglés.
- `.gitattributes` fija los saltos de línea en LF. No lo cambies: Flyway calcula checksums de los archivos SQL.
- Los secretos van en `.env`, que git ignora. La plantilla es `.env.example`.

## Publicar una versión
1. Sube la versión en `desktop/pom.xml` y `web/package.json`.
2. Escribe las notas en `docs/releases/vX.Y.Z.md`.
3. Crea y sube el tag: `git tag vX.Y.Z && git push origin vX.Y.Z`.
4. El workflow `release.yml` arma el zip del escritorio (Windows) y el de la base de datos, y crea un **release en
   borrador** con esas notas.
5. Revísalo en GitHub y publícalo.
