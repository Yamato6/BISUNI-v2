# BISUNI v2: plan de migración

## Estado
| Fase | Estado |
|---|---|
| 1. Base de datos PostgreSQL (`db/`) | ✅ Terminada y verificada (commit `712b5ae`) |
| 2. Escritorio JavaFX (`desktop/`) | 🚧 En curso. Bloques 1 (base, login, admisiones), 2 (usuarios, alumnos, profesores) y 3 (materias, grupos) ✅, con 73 pruebas. Faltan horarios y las vistas de profesor |
| 3. Portal web Next.js (`web/`) | ⏳ Pendiente; puede avanzar en paralelo a la 2 |
| 4. E2E del flujo completo + README final | ⏳ Pendiente |

## Contexto
La v1 (sep–dic 2025, carpeta `..\BISUNI`) tiene tres partes:
- Una base MySQL `bisuni2`.
- Una app Java Swing atada a NetBeans (`.form`, `nbproject/`, Ant), para administradores y profesores.
- Un portal PHP + JS sin framework, para alumnos y aspirantes.

La v2 busca cuatro cosas:
- Pasar la base a **PostgreSQL**.
- Que el escritorio sea **JavaFX + Maven**, sin depender del IDE.
- Que el portal sea **Next.js + TypeScript**.
- Corregir los problemas de la v1.

### Problemas de la v1 que corrige la v2
| Problema | Dónde se corrige |
|---|---|
| Contraseñas en texto plano | ✅ db (bcrypt + `CHECK`) |
| Credenciales de BD versionadas (`database.php`, `config.properties`) | ✅ `.env` ignorado por git |
| Admisión no idempotente: cada guardado duplicaba `student_profile` | ✅ db (`UNIQUE`) + desktop (`AdmissionService`) |
| `enrollment` nunca se llenaba | ✅ db + desktop |
| Login con contraseña en texto plano y que revelaba si el correo existía | ✅ desktop (`AuthService`: bcrypt y mensaje único) |
| `group_student` y `student_profile.id_group` se desincronizaban | ✅ db (se elimina `group_student`) |
| `schedule_teacher` duplicaba datos y no había detección de choques | ✅ db (`schedule` + vista) |
| `obtener_materias.php` duplicaba materias con varios profesores | db ✅ (`group_subject.id_teacher`) + web ⏳ |
| `main.class=bisuni.BISUNI` no existía y cada JFrame tenía su `main` | ✅ desktop (un solo `App.java`) |
| SQL concatenado en `AdminGroupScheduleEdit` y `AdminGroupScheduleView` | desktop ⏳ (`PreparedStatement`) |
| Las rutas `../api/` no coinciden en mayúsculas con la carpeta `API/` | web ⏳ |
| Datos fijos en `Main_js/profesores.js` y `localStorage` | web ⏳ |
| Sin `.gitignore` y con `build/` versionado | ✅ |

## Decisiones tomadas
- **Repositorio:** monorepo nuevo y privado. La v1 queda intacta como referencia.
- **Web:** Next.js.
- **Escritorio:** acceso por JDBC directo a Postgres; JavaFX + Maven.
- **Desviaciones respecto al plan original** (aprobadas al implementar la Fase 1):
  - Se elimina `schedule_teacher` y queda una sola tabla `schedule` con `id_teacher`; el horario del profesor es la vista `v_teacher_schedule`.
  - Todo el esquema va en `V1__schema.sql`, porque la base es nueva.
  - Se corrigieron datos inconsistentes del seed de la v1: profesores con choque de horario, CURPs de 16 caracteres y un `enrollment` que apuntaba a otras personas.

## Fase 2: Escritorio (`desktop/`, JavaFX + Maven)
- **Stack:**
  - Java 21 LTS (`--release 21`) y OpenJFX 21.
  - `javafx-maven-plugin` para `mvnw javafx:run` y para empaquetar con `jlink`/`jpackage`.
  - Driver PostgreSQL JDBC y HikariCP.
  - `at.favre.lib:bcrypt` para las contraseñas.
  - AtlantaFX para el tema visual.
  - JUnit + Postgres embebido (`io.zonky.test:embedded-postgres`) + Flyway para las pruebas de integración; no requieren Docker.
- **Paquetes `mx.bisuni.desktop`:**
  - `config/`: variables de entorno o un `bisuni.properties` externo, ignorado por git.
  - `db/`: `Database` con un pool Hikari.
  - `model/`: `record`s (User, Student, Teacher, Subject, Group, ScheduleEntry, Grade, Aspirant).
  - `dao/`: un DAO por agregado, solo con `PreparedStatement`.
  - `service/`:
    - `AuthService`: bcrypt y control de rol.
    - `AdmissionService`: solo procesa los cambios; transacción con `enrollment`.
    - `ScheduleService`: traduce las violaciones de `UNIQUE` a mensajes claros.
    - `GradeService`: UPSERT.
  - `session/`: `UserSession` inmutable con el id de perfil (admin o teacher).
  - `ui/`: FXML con su controller y un `Navigator` que cambia de vista en **una sola ventana** con menú lateral.
  - `App.java`: punto de entrada único que abre el Login.
- **Pantallas:** las de la v1 sirven como especificación funcional (`..\BISUNI\Java Netbeans Proyect\BISUNI\src\bisuni\`).
  - **Admin:**
    - Usuarios: lista, alta y edición.
    - Alumnos: lista y perfil.
    - Profesores: lista, perfil y materias.
    - Materias: lista, alta y edición.
    - Grupos: lista, alta, edición, alumnos, materias con su profesor, y tutor.
    - Horarios: de grupo y de profesor, en una cuadrícula de lunes a viernes por 9 bloques.
    - Admisiones.
  - **Profesor:** mis materias, mi horario, mis grupos, alumnos de un grupo y captura de calificaciones.
  - Las ~32 pantallas Swing se agrupan en unas ~15 vistas, con un componente reutilizable `ScheduleGrid`.
- **Orden de trabajo:** login → menú → admisiones → usuarios/alumnos/profesores → materias/grupos → horarios → vistas de profesor y calificaciones.

### Bloque 1 ✅: base, login, shell y admisiones
- **Base:**
  - Proyecto Maven con Wrapper (Maven 3.9.16) y `--release 21`.
  - Versiones: JavaFX 21.0.13, AtlantaFX 2.1 (PrimerLight con los verdes de la v1), HikariCP 7 y bcrypt de favre.
  - Sin `module-info` por ahora; el empaquetado con `jlink`/`jpackage` se decide más adelante.
- **Configuración (`AppConfig`):** se lee de las variables de entorno, luego de `desktop/bisuni.properties` y al final del `.env` de la raíz. Si falta `DB_PASSWORD`, el Login lo muestra en lugar de fallar.
- **`AuthService`:**
  - Normaliza el email y verifica con bcrypt.
  - Responde lo mismo si el correo no existe o la contraseña es incorrecta, y en ese caso también compara contra un hash de relleno para que el tiempo de respuesta no lo delate.
  - Rechaza usuarios inactivos, alumnos y aspirantes.
  - Para un Admin, actualiza `last_access`.
- **`AdmissionService`:**
  - Recibe solo las filas modificadas (`from` → `to`).
  - Aplica todo el lote en una transacción, con control optimista: `UPDATE ... WHERE status = from`.
  - `Accepted` es final.
  - Al aceptar: cambia el rol a Student, crea `student_profile` y crea `enrollment` con `approved_by`.
- **UI:**
  - Una sola ventana con `Navigator`; el shell tiene un menú lateral según el rol (lo que falta aparece como "Próximamente").
  - La tabla de admisiones resalta las filas cambiadas y bloquea las aceptadas.
  - Pide confirmación antes de guardar y antes de salir o cambiar el filtro con cambios pendientes.
- **Pruebas:**
  - Postgres 17 embebido (`io.zonky`), con Flyway aplicando `db/migrations` y `db/seed` en una base plantilla. Cada prueba clona la plantilla para empezar limpia.
  - `DevDatabase` (`mvnw exec:java`) levanta la misma BD de forma persistente para la prueba manual.

### Bloque 2 ✅: usuarios, alumnos y profesores
- **Usuarios:**
  - Lista con búsqueda (nombre, usuario o correo) y filtros por rol y estatus.
  - **Alta:** crea el usuario y su perfil en una transacción. Los roles disponibles son Admin (con puesto), Profesor (con especialidad y teléfono) y Alumno (con carrera y semestre, como alta directa).
  - **Edición:** datos de acceso, estatus y, opcionalmente, una contraseña nueva.
- **Correcciones respecto a la v1:**
  - El rol ya no se cambia después de crear la cuenta. En la v1 se podía cambiar sin migrar los perfiles.
  - Los aspirantes no se crean aquí. En la v1 se creaban sin CURP; ahora se registran solo desde la web.
  - Usuario y correo se guardan en minúsculas, y los duplicados dan mensajes claros.
  - Un admin no puede desactivar su propia cuenta.
- **Alumnos:**
  - Lista con filtros por carrera, grupo ("Sin grupo" incluido), estatus y búsqueda.
  - La edición cubre los datos de usuario, la carrera, el semestre, el grupo (solo de su carrera) y el promedio.
  - **Corrección respecto a la v1:** cambiar de grupo **ya no borra las calificaciones**.
- **Profesores:**
  - Lista con conteo de materias y búsqueda (también por especialidad).
  - La edición cubre el perfil, la especialidad y el teléfono.
  - El diálogo de materias agrega o quita `teacher_subject` al momento. Muestra en qué grupos imparte cada materia, y no deja quitar una que esté asignada en `group_subject`.
- **Infraestructura de UI:**
  - `FormDialog`: formulario modal que guarda en segundo plano y se queda abierto con el error si falla.
  - `UserFields`, `ListSupport` (columnas, doble clic, búsqueda con espera de 300 ms) y `Option` (filtros con "Todos").
- **Errores SQL:** `SqlErrors` traduce las violaciones de `UNIQUE`, FK y `CHECK` a mensajes en español, según el nombre de la restricción.
- **Pruebas:**
  - Servicios: `UserServiceTest`, `StudentServiceTest` y `TeacherServiceTest`.
  - `ViewsSmokeTest`: carga cada FXML con su controlador contra una BD de prueba y verifica que la tabla se llene.

### Bloque 3 ✅: materias y grupos
- **Materias:**
  - Lista con filtros por carrera, semestre y estatus, y conteos de grupos y de profesores habilitados.
  - Alta y edición con nombre, carrera, semestre, créditos, horas por semana y estatus.
  - El nombre es único por carrera.
  - Una materia asignada a grupos ya no puede cambiar de carrera.
- **Grupos:**
  - Lista con filtros, tutor y conteos de alumnos, materias y materias "sin profesor" (resaltadas, porque no se pueden agendar).
  - Alta y edición con nombre (en mayúsculas), carrera, semestre, tutor y estatus. La carrera solo cambia si el grupo no tiene alumnos ni materias.
- **«Materias y alumnos…»**, pestaña de materias:
  - Solo se pueden agregar materias activas de la carrera del grupo. La v1 permitía cualquiera.
  - El profesor se elige entre los habilitados (`teacher_subject`).
  - Cambiar el profesor actualiza el horario en cascada. Si el nuevo profesor ya tiene clase en esos bloques, se rechaza con un mensaje claro.
  - No se puede dejar sin profesor una materia que tiene clases en el horario.
  - Quitar una materia avisa cuántas clases del horario se borran. Si ya tiene calificaciones, no se puede quitar.
- **«Materias y alumnos…»**, pestaña de alumnos:
  - Se agregan alumnos activos de la misma carrera; si vienen de otro grupo, pide confirmación para moverlos.
  - Quitar a un alumno del grupo lo deja sin grupo y conserva sus calificaciones.
- **Errores:**
  - `SqlErrors.translate(e, fallback, overrides)` permite mensajes según la operación, porque la misma FK significa cosas distintas.
  - `service/Rejected` concentra los rechazos de negocio dentro de una transacción.
- **Pruebas:** `SubjectServiceTest` y `GroupServiceTest` (cascada del horario, choque de profesor, calificaciones que bloquean y alumnos de otra carrera). También se agregaron las dos vistas a `ViewsSmokeTest`.

## Fase 3: Web (`web/`, Next.js)
- **Stack:**
  - Next.js (App Router) con TypeScript estricto.
  - Drizzle ORM + `postgres`; el esquema se genera con `drizzle-kit pull`.
  - Auth.js (credentials, bcryptjs, JWT con `role` e `id_student`).
  - Zod para validar, Tailwind para estilos, Vitest y Playwright para pruebas.
- **Mapeo v1 → v2:** Server Actions y Server Components reemplazan a `API/*.php` y a los `fetch` manuales.

| v1 | v2 |
|---|---|
| `Main_index/index.php` | `app/page.tsx` |
| `login.php` + `API/login_process.php` | `app/login` + Auth.js |
| `aspirante-registro.php` + `registrar_aspirante.php` + `obtener_carreras.php` | `app/aspirantes/registro` + action `registerAspirant` (transacción, folio `ASP-000123`) |
| `aspirante-estatus.php` + `consultar_estatus.php` | `app/aspirantes/estatus` (CURP + folio) |
| `dashboard-estudiante.php` | `app/(alumno)/dashboard` |
| `calificaciones.php` + `obtener_materias.php` | `app/(alumno)/calificaciones` |
| `horario.php` + `obtener_horario.php` | `app/(alumno)/horario` |
| `actualizar-datos.php` + `actualizar_datos.php` | `app/(alumno)/perfil` (cambiar la contraseña exige la actual) |
| `logout.php` | `signOut()` |
| `Main_js/profesores.js` | `app/(alumno)/profesores` con datos reales |
| `API/insertar_datos_prueba.php` | se elimina (lo cubre el seed) |

- Un `middleware.ts` protege `(alumno)/*` y exige `role === 'Student'`.
- Se conserva la identidad visual de la v1 (colores, fuente de `Main_font/`, logo de `assets_images/`).
- **Orden de trabajo:** login + layout → dashboard/calificaciones/horario → registro y estatus de aspirante → perfil → profesores.

## Verificación
- **BD:**
  - `docker compose up`, o el Postgres embebido.
  - Comprobar que se rechazan horarios duplicados, CURPs duplicadas y calificaciones fuera de rango. ✅ Hecho.
- **Escritorio:**
  - `mvnw verify`, incluida la idempotencia de admisión (guardar dos veces no duplica nada).
  - `mvnw javafx:run` para la prueba manual.
- **Web:**
  - `npm run typecheck && npm run lint && npm test`.
  - Playwright: registro de aspirante → estatus → login de alumno → horario y calificaciones.
- **Flujo cruzado:**
  1. Registrar un aspirante en la web.
  2. Aceptarlo en el escritorio y comprobar que aparece en `enrollment`.
  3. Asignarlo a un grupo y capturar calificaciones como profesor.
  4. Comprobar que el alumno las ve en la web.
