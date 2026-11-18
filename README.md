# BISUNI v2

Sistema de control escolar universitario. Es la segunda versión del Proyecto Integrador I (TDSM3A, sep–dic 2025):
la v1 (MySQL, Java Swing/NetBeans y PHP) reescrita con PostgreSQL, JavaFX y Next.js, corrigiendo sus errores.

```
            ┌──────────────────────────────┐        ┌───────────────────────────────┐
            │  desktop/  (JavaFX + Maven)  │        │  web/  (Next.js + TypeScript) │
            │  administradores y profesores│        │  alumnos y aspirantes         │
            └──────────────┬───────────────┘        └───────────────┬───────────────┘
                     JDBC + HikariCP                         Drizzle + postgres
                           └─────────────────┬──────────────────────┘
                                   ┌─────────▼─────────┐
                                   │  db/  PostgreSQL  │  esquema = migraciones Flyway
                                   └───────────────────┘
```

| Carpeta | Qué es | Usuarios |
|---|---|---|
| [`db/`](db/) | Esquema PostgreSQL (migraciones Flyway) y datos de prueba | — |
| [`desktop/`](desktop/) | App de escritorio: control escolar completo (ver [Funciones](#funciones)) | Administradores y profesores |
| [`web/`](web/) | Portal del alumno y registro de aspirantes | Alumnos y aspirantes |

## Funciones

| Quién | Dónde | Qué puede hacer |
|---|---|---|
| Administrador | Escritorio | Panel de inicio con alertas; periodos escolares y convocatoria; carreras, materias, grupos y horarios; admisiones (con matrícula); alumnos (bajas, reingresos, egreso); reinscripción de grupos al semestre siguiente; usuarios y contraseñas temporales; reabrir actas; avisos; bitácora de cambios |
| Profesor | Escritorio | Sus clases, horario y grupos; captura de calificaciones y cierre de actas; pase de lista; avisos para sus grupos |
| Alumno | Web | Inicio con avisos y alertas; calificaciones, kárdex con avance de créditos, horario, asistencia y profesores; constancia, kárdex y boleta en PDF; perfil |
| Aspirante | Web | Registro en la convocatoria abierta y consulta de estatus con CURP + folio |

## Inicio rápido

Requisitos: **Java 21+** (escritorio) y **Node 20.9+** (web). Maven no hace falta: se usa el wrapper `mvnw`.

1. Configura el `.env` (lo comparten las dos apps):
   ```bash
   cp .env.example .env
   ```
   Define `DB_PASSWORD` (y la misma en `DATABASE_URL`) y `AUTH_SECRET`
   (`node -e "console.log(require('crypto').randomBytes(32).toString('base64'))"`).

2. Levanta la base de datos con **una** de estas opciones:

   | Opción | Comandos |
   |---|---|
   | **Docker** | `docker compose up -d` (Flyway aplica esquema + seed y termina) |
   | **PostgreSQL instalado** (17+) | ver [abajo](#con-un-postgresql-instalado) |
   | **Embebido, sin instalar nada** | `cd desktop && ./mvnw exec:java` (déjalo corriendo) |

3. Arranca las apps:
   ```bash
   cd desktop && ./mvnw javafx:run          # escritorio
   cd web && npm install && npm run dev      # portal en http://localhost:3000
   ```
   Si cambias el `.env`, reinicia `npm run dev`.

### Con un PostgreSQL instalado
Desde la raíz del repo (PowerShell; pide la contraseña del usuario `postgres`):

```powershell
$pw = (Select-String '^DB_PASSWORD=(.*)' .env).Matches[0].Groups[1].Value
& "C:\Program Files\PostgreSQL\18\bin\psql.exe" -h localhost -U postgres -d postgres -v user=bisuni -v db=bisuni -v "pass=$pw" -f db/local-setup.sql
cd desktop
./mvnw exec:java "-Dexec.mainClass=mx.bisuni.desktop.dev.DbMigrate"    # migraciones + seed
```

Con `"-Dexec.args=--sin-seed"` aplica solo las migraciones (el seed borra los datos con `TRUNCATE`).
Con `"-Dexec.args=--reset"` borra el esquema completo y lo vuelve a crear; se necesitó una vez, cuando la Fase 5
reescribió `V1__schema.sql` (desde entonces, cada cambio va en una migración nueva).

### El embebido
`./mvnw exec:java` levanta PostgreSQL 17 en el puerto de `DB_PORT` con los datos en `%TEMP%/bisuni-devdb`
(`-Dexec.args=--reset` la recrea). Si ya tienes un PostgreSQL en ese puerto, usa otro (p. ej. 5433) en el `.env`.

## Cuentas de prueba

Todas usan la contraseña `password123`.

| Rol | Cuenta | Dónde entra |
|---|---|---|
| Admin | `admin.master@bisuni.edu` | Escritorio |
| Profesor | `juan.perez@bisuni.edu` (IT-1A e IT-1B, tutor de IT-1A) | Escritorio |
| Alumno con grupo y calificaciones | `student001@bisuni.edu` (IT-1A) | Web |
| Alumno con historial de dos periodos | `student051@bisuni.edu` (IT-1A en 2026-1, hoy IT-2A) | Web |
| Alumno en riesgo por faltas y con materia adeudada | `student053@bisuni.edu` | Web |
| Alumno con baja definitiva (no entra) | `student060@bisuni.edu` | Web |
| Alumno aceptado, sin grupo | `aspirant05@bisuni.edu` | Web |
| Aspirante pendiente | `aspirant01@bisuni.edu` · CURP `BEXA070312MASLND01` · folio `ASP-000001` | Web (estatus) |

Para registrar un aspirante nuevo en la web sirve cualquier CURP con formato válido, p. ej. `GOMA050101HASRRL09`.

## Pruebas

Ninguna prueba usa tu base de datos: cada suite levanta su propio PostgreSQL 17 embebido con las migraciones y
el seed reales.

| Proyecto | Comando | Qué cubre |
|---|---|---|
| `desktop/` | `./mvnw verify` | 143 pruebas: servicios contra la base real (con triggers y bitácora) y humo de cada vista |
| `web/` | `npm run typecheck && npm run lint && npm test` | 45 pruebas Vitest: reglas, login, kárdex, asistencia, avisos y PDF contra la base real |
| `web/` | `npm run test:e2e` | 17 pruebas Playwright, incluido el **flujo completo entre las dos apps** |

> En Windows, corre las pruebas web desde una terminal cuya ruta empiece con `C:\` (mayúscula). Si la ruta
> queda como `c:\` (pasa al abrirla desde algunos editores), Vitest carga su módulo dos veces y falla con
> "Vitest failed to find the runner".

El flujo completo (`web/e2e/flujo-completo.spec.ts`) usa el código real de ambas aplicaciones:
1. Un aspirante se registra en la convocatoria de la web.
2. El escritorio lo acepta (con matrícula) y lo asigna a un grupo.
3. La profesora le captura calificaciones, cierra un acta, le pone una falta y publica un aviso.
4. El admin le restablece la contraseña.
5. El alumno crea su contraseña en la web y ve el aviso, la alerta de faltas, sus calificaciones, el kárdex con
   la materia ya definitiva, su asistencia y su constancia en PDF.

Las acciones del escritorio se ejecutan con sus servicios reales mediante `desktop/.../dev/DesktopCli.java`
(vía `mvnw`), por eso esa prueba necesita Java.

## Estructura

```
db/
  migrations/V1__schema.sql     esquema (única fuente de verdad; nunca se edita una migración aplicada)
  seed/R__seed_dev.sql          datos de prueba (repeatable, empieza con TRUNCATE)
  local-setup.sql               crea usuario y base en un PostgreSQL instalado
desktop/                        Java 21, JavaFX 21, JDBC + HikariCP, AtlantaFX
  src/main/java/mx/bisuni/desktop/{config,db,model,dao,service,session,ui}
  src/test/java/...             pruebas + herramientas de desarrollo (DevDatabase, DbMigrate, DesktopCli)
web/                            Next.js 16, Drizzle, Auth.js v5, Tailwind 4
  src/app/(publico)             inicio, login, registro y estatus de aspirantes
  src/app/(alumno)              inicio, calificaciones, kárdex, horario, asistencia, profesores, documentos (PDF), perfil
  src/app/cambiar-contrasena    cambio obligatorio tras una contraseña temporal
  src/lib                       reglas, consultas, PDF y la DAL (requireStudent)
  src/db/generated              esquema generado con `npm run db:pull` (no se edita)
docs/PLAN.md                    plan, decisiones y detalle de cada fase
```

## Qué cambió respecto a la v1

### Base de datos (MySQL → PostgreSQL)

| v1 | v2 |
|---|---|
| Contraseñas en texto plano | bcrypt; un `CHECK` rechaza texto plano |
| `group_student` y `student_profile.id_group` se desincronizaban | Solo `student_profile.id_group`, con FK que exige alumno y grupo de la misma carrera |
| `schedule_group` y `schedule_teacher` duplicados, sin detectar choques | Una tabla `schedule` con `UNIQUE` por grupo y por profesor; el horario del profesor es la vista `v_teacher_schedule` |
| No se sabía qué profesor da cada materia a cada grupo | `group_subject.id_teacher`, validado contra `teacher_subject` |
| `final_grade` calculado a mano | Columna generada: promedio de los 3 parciales |
| Calificaciones duplicables | `UNIQUE (id_student, id_subject, id_group)` y captura con `INSERT ... ON CONFLICT` |
| `enrollment` nunca se llenaba | Lo llena la admisión, en la misma transacción |
| CURP sin validar; GPA en `DECIMAL(3,2)` (no admitía 10) | CURP `CHAR(18)` única con formato validado; `NUMERIC(4,2)` entre 0 y 10 |

### Aplicaciones

| v1 | v2 |
|---|---|
| Admisión reprocesaba todos los aspirantes y duplicaba perfiles | Solo los cambios, en una transacción, con control optimista; aceptar es definitivo |
| Swing atado a NetBeans, un `main` por ventana, SQL concatenado | JavaFX + Maven, una sola ventana, solo `PreparedStatement` |
| Cambiar de grupo borraba las calificaciones | Se conservan (cada una ligada al grupo donde se cursó) |
| Un profesor "habilitado" veía todos los grupos de la materia | Solo las clases que imparte (`group_subject.id_teacher`) |
| Portal PHP con rutas rotas, datos fijos en JS y `localStorage` | Next.js con datos reales de la base |
| Estatus de aspirante solo con la CURP | CURP **y** folio `ASP-000123` |
| Contraseñas de 4 caracteres; cambiarla no pedía la actual | 8 caracteres mínimo; cambiarla exige la actual |
| Credenciales de la base en el repositorio | `.env` ignorado por git (plantilla `.env.example`) |

Detalle completo de decisiones y de cada fase en [docs/PLAN.md](docs/PLAN.md).
