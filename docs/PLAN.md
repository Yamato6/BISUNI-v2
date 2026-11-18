# BISUNI v2: plan de migración

## Estado
| Fase | Estado |
|---|---|
| 1. Base de datos PostgreSQL (`db/`) | ✅ Terminada y verificada (commit `712b5ae`) |
| 2. Escritorio JavaFX (`desktop/`) | ✅ Terminada: bloques 1 a 5, con 100 pruebas (servicios + humo de vistas) |
| 3. Portal web Next.js (`web/`) | ✅ Terminada: bloques 1 a 3, con 35 pruebas Vitest y 10 E2E |
| 4. E2E del flujo completo + README final | ✅ Terminada: E2E web ↔ escritorio con el código real de ambas apps; README reescrito |
| 5. Producto de gestión escolar | ✅ Terminada: bloques A a E. Periodos, matrícula, actas, asistencia, avisos, bitácora, kárdex y documentos PDF; escritorio 143 pruebas, web 45 Vitest + 17 E2E |

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

### Bloque 4 ✅: horarios
- **`ScheduleGrid`:** componente reutilizable de lunes a viernes por los bloques de `time_slots`. Cada materia tiene su color fijo y la segunda línea es configurable (profesor o grupo). Se reutilizará en "Mi horario" del profesor.
- **Horario por grupo (editable):**
  - Al hacer clic en un bloque se elige la materia del grupo; el profesor sale de `group_subject`. También se puede vaciar el bloque.
  - Las materias sin profesor aparecen deshabilitadas.
  - Un panel lateral muestra, por materia, las horas programadas contra las semanales: verde si están completas, ámbar si faltan y rojo si sobran.
- **Horario por profesor (solo consulta):** se deriva de `v_teacher_schedule`. En la v1 había dos tablas que se editaban por separado y se desincronizaban.
- **Choques:** `ScheduleService.assign` revisa antes de guardar si el profesor ya tiene clase y dice dónde ("Juan Pérez ya tiene clase el lunes de 07:00 a 08:00 en IT-1B"). Los `UNIQUE` de la base quedan como respaldo.
- **SQL seguro:** todo usa `PreparedStatement`. La v1 concatenaba el `groupId` y el filtro de carrera en el SQL.
- **Seed:** las materias de IT-1A tienen 15 clases programadas a la semana contra 4 o 5 `weekly_hours`. No se bloquea el exceso; solo se marca en rojo.
- **Pruebas:** `ScheduleServiceTest` (11 casos, entre ellos el choque que nombra al otro grupo y el horario del profesor que sigue a los cambios del grupo) y la vista en `ViewsSmokeTest`.


### Bloque 5 ✅: vistas del profesor y calificaciones
- **Menú del profesor:** Mis clases (inicio), Mi horario, Mis grupos y Calificaciones.
- **Mis clases** sale de `group_subject.id_teacher`, es decir, de quién da realmente la clase.
  - En la v1 se usaba `teacher_subject`, así que cualquier profesor *habilitado* para una materia veía todos los grupos que la cursan.
  - Para cada clase muestra los alumnos, las horas en el horario y cuántos tienen calificación final. Con doble clic o con el botón se abre la captura con esa clase elegida.
- **Mi horario** reutiliza `ScheduleGrid` en modo de solo consulta (`v_teacher_schedule`).
- **Mis grupos** muestra los grupos donde imparte o es tutor, con la lista de alumnos de cada uno. Solo puede ver los alumnos de esos grupos; el servicio lo valida.
- **Calificaciones** funciona como hoja de captura de toda la clase.
  - **Captura:** las celdas de parciales son campos siempre editables y aceptan coma decimal. Un valor inválido se marca en rojo. El final se muestra en vivo con la misma fórmula que la base.
  - **Guardado:** las filas cambiadas se resaltan. Al guardar, solo esas filas se envían, en una transacción con UPSERT (`ON CONFLICT ... DO UPDATE`).
  - **Columna generada:** `final_grade` nunca se escribe desde Java. En la v1 se calculaba y se escribía desde la app.
  - **Permisos:** solo el profesor asignado (`group_subject.id_teacher`) puede ver o capturar la hoja.
  - **Alumnos fuera de la clase:** se rechazan y no se guarda nada.
  - **Alumnos que cambiaron de grupo:** si ya tenían calificaciones de la clase, siguen visibles con la nota "Ya no está en el grupo".
- **Pruebas:** `GradeServiceTest` (12 casos) y las cuatro vistas en `ViewsSmokeTest`.

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

- Un `proxy.ts` (Next 16 renombró `middleware`) protege `(alumno)/*` y exige `role === 'Student'`; la DAL lo verifica contra la base.
- Se conserva la identidad visual de la v1 (colores, fuente de `Main_font/`, logo de `assets_images/`).
- **Orden de trabajo:** login + layout → dashboard/calificaciones/horario → registro y estatus de aspirante → perfil → profesores.


### Bloque 1 ✅: base, login, inicio, calificaciones y horario
- **Stack:**
  - Next.js 16.4 (App Router, Turbopack) con React 19 y TypeScript 5.9. TS 7 todavía no lo soporta el chequeo de Next.
  - Drizzle 0.45 + `postgres`, Auth.js v5 (beta.32, que ya soporta Next 16), Zod 4 y Tailwind 4 (loader `@tailwindcss/turbopack`).
  - Vitest 5 y Playwright 1.65.
- **Esquema:** `npm run db:pull` genera `src/db/generated` y borra la migración que drizzle-kit crea de paso. La fuente de verdad siguen siendo las migraciones de Flyway.
- **`.env` compartido:** se lee el de la raíz. `next.config.ts` lo carga con `loadEnvConfig(..., forceReload)`, porque sin forzar la recarga Next ya tiene en caché los `.env` de `web/` y no lee nada más.
- **Autenticación:**
  - `proxy.ts` (antes `middleware`) solo hace la revisión optimista de la cookie.
  - La verificación segura es `requireStudent()` en `lib/dal.ts`. Comprueba en la base que la cuenta siga activa y siga siendo de alumno; si no, cierra la sesión vía `/salir`.
  - **Login:** el correo se normaliza y la contraseña se compara con bcrypt (con un hash de relleno si el correo no existe).
  - **Mensajes:** son propios para cuenta desactivada, aspirante pendiente y personal ("usa la app de escritorio"). Credenciales incorrectas siempre dan el mismo mensaje genérico.
- **Páginas:**
  - Inicio público (misión, visión y valores de la v1) y login.
  - Dashboard con datos reales; la v1 tenía "8 materias" y "6 aprobadas" escritos a mano.
  - Calificaciones: materias del grupo actual con su profesor real, más grupos anteriores. La regla de la v1 se conserva: aprobada con final de 6 o más y "en proceso" si falta algún parcial.
  - Horario en cuadrícula, con un color por materia.
- **Identidad de la v1:** verdes `#2ecc71` / `#27ae60`, fuente NationalPark para títulos y logo `bis.png`.
- **Pruebas:**
  - Vitest: unitarias más integración (`authorizeStudent` y las consultas del alumno) contra PostgreSQL 17 embebido, con plantilla clonada por archivo.
  - Playwright: el servidor E2E levanta su propia base embebida y nunca toca la de desarrollo.


### Bloque 2 ✅: registro y estatus de aspirantes
- **Registro** (`/aspirantes/registro`):
  - Campos: nombre, apellidos, CURP (mismo formato que el `CHECK` de la base), correo, contraseña de 8 caracteres o más (la v1 aceptaba 4) con confirmación, y carrera activa.
  - En una transacción se crean el usuario (rol Aspirant, bcrypt, `username` = CURP en minúsculas) y su `aspirant_profile`.
  - Al terminar se muestra el folio `ASP-000123`. Esa misma cuenta es con la que entrará al portal si lo aceptan.
  - Un correo o una CURP repetidos dan mensajes claros por campo y no dejan cuentas sueltas.
- **Estatus** (`/aspirantes/estatus`):
  - Se consulta con **CURP + folio**; la v1 pedía solo la CURP. El folio se acepta como `ASP-000123`, `asp123` o `123`.
  - Si los datos no coinciden, el mensaje no dice cuál de los dos falló.
  - Muestra el estatus (En revisión / Aceptado / No aceptado). Si fue aceptado, también la fecha de inscripción y el acceso al portal.
- **Pruebas:**
  - Vitest: reglas (`aspirant-rules`) e integración del registro y la búsqueda.
  - E2E del **flujo cruzado**: registro en la web → estatus pendiente → aceptación (los mismos pasos que `AdmissionService` del escritorio) → estatus aceptado → login como alumno sin grupo.


### Bloque 3 ✅: mi perfil y mis profesores
- **Mi perfil** (`/perfil`):
  - Los datos académicos (correo-usuario, carrera, semestre y grupo) son solo de consulta; los administra control escolar.
  - Nombre y apellidos editables, como en la v1.
  - **Cambio de contraseña:** exige la actual, pide 8 caracteres o más y que sea distinta de la actual. La v1 no pedía la actual, aceptaba 4 caracteres y la guardaba en texto plano.
- **Mis profesores** (`/profesores`):
  - Muestra quienes imparten las materias del grupo actual (`group_subject.id_teacher`) y su tutor, con materias, correo, especialidad y teléfono.
  - En la v1 eran datos fijos escritos en `Main_js/profesores.js`.
- **Pruebas:**
  - Vitest: validación, cambio de nombre, cambio de contraseña que exige la actual, y profesores con tutor.
  - E2E: cambio de nombre y contraseña, y nuevo login con la contraseña nueva.


## Fase 4 ✅: E2E del flujo completo y README final
- **`web/e2e/flujo-completo.spec.ts`:** prueba el flujo cruzado del plan con el código **real** de las dos aplicaciones, contra la base embebida del servidor E2E.
  1. **Web:** un aspirante se registra; mientras está pendiente, el login le avisa que su solicitud no ha sido aceptada.
  2. **Escritorio:** `AdmissionService` lo acepta y `GroupService` lo asigna a BA-1A. Volver a aceptarlo se rechaza sin duplicar nada.
  3. **Escritorio:** la profesora del grupo inicia sesión (`AuthService`) y captura parciales (`GradeService`).
  4. **Web:** el alumno ve su grupo, finales de 8.83 (aprobada) y 4.50 (reprobada) calculadas por la base, su horario y a su tutora; el estatus de la solicitud aparece aceptado con su fecha de inscripción.
- **Llamadas al escritorio:** Playwright ejecuta las acciones con `desktop/src/test/java/mx/bisuni/desktop/dev/DesktopCli.java` (vía `mvnw exec:java`). La conexión le llega con las variables `DB_*`, que tienen prioridad sobre el `.env`.
- **README** reescrito: arquitectura, inicio rápido con las tres opciones de base de datos, cuentas de prueba, pruebas, estructura y cambios respecto a la v1.
- **Totales:** escritorio 100 pruebas; web 35 Vitest + 11 E2E.

## Fase 5 ✅: BISUNI como producto de gestión escolar
Las fases 1 a 4 modelaban "un semestre congelado": sin periodos, con un promedio que se escribía a mano y
sin historial, asistencia ni documentos. La Fase 5 lo acerca a un sistema completo de control escolar.

**Decisión:** como todavía no había datos reales, se **reescribió `V1__schema.sql` una sola vez**. A partir de
aquí vuelve la regla: todo cambio de esquema va en una migración nueva (`V2__...`). La base local se recrea con
`DbMigrate --reset`.

### Bloque A ✅: modelo nuevo (base de datos)
- **Periodos** (`academic_period`): planeado → activo → cerrado, un solo activo (índice único parcial) y
  convocatoria de aspirantes (`admissions_open`). Cada grupo pertenece a un periodo; el nombre se repite entre
  periodos. Cada aspirante aplica a una convocatoria.
- **Alumno:** matrícula `{año}{clave de carrera}{consecutivo}` (`next_enrollment_number`, con candado) y
  situación académica (activo, baja temporal, baja definitiva, egresado). El promedio ya no se guarda: es la
  vista `v_student_gpa`.
- **Carrera:** clave (`IT`) y número de semestres.
- **Actas** (`group_subject.grades_closed_at/by`) y **guardas por trigger**: no se capturan calificaciones con
  el acta cerrada ni fuera del periodo activo; la asistencia solo en el periodo activo y dentro de sus fechas; no
  se asignan alumnos a grupos de periodos cerrados.
- **Asistencia, avisos** (para todos, una carrera o un grupo, con vencimiento) y **bitácora** (`audit_log`),
  llenada por triggers con el actor de `bisuni.actor`. No guarda hashes de contraseña.
- **Vistas:** `v_kardex` (aprobada, reprobada o en curso) y `v_attendance_summary`.
- **Seed:** periodo 2026-1 cerrado con historial y 2026-2 activo; una generación de IT con dos periodos,
  reprobadas, una baja definitiva, un alumno en riesgo por faltas y avisos. Las horas del horario cuadran con
  `weekly_hours`.

### Bloque B ✅: escritorio, administración académica
- Vistas **Periodos** (crear, activar, cerrar y convocatoria; cerrar exige actas cerradas) y **Carreras**.
- **Grupos:** filtro por periodo; los de periodos cerrados son historial. **Reinscribir / egresar**: crea el grupo
  del semestre siguiente con sus materias y pasa a los alumnos activos; en el último semestre egresa a quienes
  no deben materias.
- **Alumnos:** matrícula, situación y promedio calculado; cambio de situación (bajas, reingreso y egreso).
- **Contraseñas:** restablecimiento con contraseña temporal; cambio obligatorio al entrar (escritorio y web).
- **Horarios:** tope de bloques por `weekly_hours`; los choques de profesor se revisan dentro del periodo.

### Bloque C ✅: escritorio, profesor, avisos, bitácora y panel
- **Asistencia** (profesor): pase de lista por clase y fecha, con el porcentaje de faltas y el riesgo (más del 20 %).
- **Cierre de actas** desde Calificaciones; el admin las reabre desde Grupos.
- **Avisos** (admin a todos, una carrera o un grupo; el profesor solo a sus grupos).
- **Bitácora** filtrable y legible ("Parcial 1: 8.50 → 10.00").
- **Panel de inicio** del admin: cifras del periodo activo, alumnos por carrera, materias con más
  reprobados y alertas (sin profesor, horario incompleto, riesgo por faltas).

### Bloque D ✅: portal del alumno
- Cambio de contraseña obligatorio (`/cambiar-contrasena`) tras un restablecimiento.
- Inicio con avisos vigentes, alertas de faltas y materias adeudadas, promedio y avance de carrera.
- **Kárdex** por periodo con créditos aprobados frente al total de la carrera.
- **Asistencia** por materia con fechas.
- **Documentos PDF** (`@react-pdf/renderer`): constancia de estudios o de egreso, kárdex y boleta por periodo.
- Los alumnos con baja definitiva ya no entran; la DAL lo revisa en cada página.

### Bloque E ✅: E2E y documentación
- `DesktopCli` gana `grade-rest`, `close-acta`, `attendance`, `announce` y `reset-password`, y encadena varios
  comandos con `+` en una sola JVM.
- `flujo-completo.spec.ts`:
  1. Registro en la convocatoria.
  2. Admisión con matrícula.
  3. Calificaciones y acta cerrada (el escritorio ya no deja modificarla).
  4. Falta y aviso.
  5. Contraseña temporal.
  6. El alumno crea su contraseña y ve aviso, alerta, calificaciones, kárdex definitivo, asistencia y constancia PDF.

### Fuera de alcance (pendiente)
- Recursamiento de materias reprobadas e inscripción por materia; los alumnos irregulares se reinscriben con su grupo.
- Ponderación configurable de parciales.
- Envío de correos.
- Portal web de profesores.
- Empaquetado con jpackage.

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
