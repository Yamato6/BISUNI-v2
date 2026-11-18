# De la v1 a la v2

BISUNI nació como el **Proyecto Integrador I** (TDSM3A, septiembre a diciembre de 2025), que integró tres
materias: Programación Orientada a Objetos, Bases de Datos y Diseño de Software. La v1 tenía tres partes:
- una base MySQL;
- una app de escritorio en Java Swing atada a NetBeans;
- un portal en PHP y JavaScript sin framework.

La v2 la reescribe por completo con PostgreSQL, JavaFX y Next.js, corrige sus errores y la convierte en un
sistema de control escolar completo. El plan y las decisiones de cada fase están en [PLAN.md](PLAN.md).

## Base de datos (MySQL → PostgreSQL)

| v1 | v2 |
|---|---|
| Contraseñas en texto plano | bcrypt; un `CHECK` rechaza texto plano |
| `group_student` y `student_profile.id_group` se desincronizaban | Solo `student_profile.id_group`, con una FK que exige que alumno y grupo sean de la misma carrera |
| `schedule_group` y `schedule_teacher` duplicados, sin detectar choques | Una tabla `schedule` con `UNIQUE` por grupo y por profesor; el horario del profesor es la vista `v_teacher_schedule` |
| No se sabía qué profesor daba cada materia a cada grupo | `group_subject.id_teacher`, validado contra `teacher_subject` |
| `final_grade` calculado a mano | Columna generada: promedio de los 3 parciales |
| Calificaciones duplicables | `UNIQUE (id_student, id_subject, id_group)` y captura con `INSERT ... ON CONFLICT` |
| `enrollment` nunca se llenaba | Lo llena la admisión, en la misma transacción |
| CURP sin validar; promedio en `DECIMAL(3,2)` (no admitía 10) | CURP `CHAR(18)` única con formato validado; el promedio se calcula con una vista |
| Un solo "semestre congelado": sin periodos ni historial | Periodos escolares, matrícula, actas, kárdex, asistencia, avisos y bitácora |

## Aplicaciones

| v1 | v2 |
|---|---|
| La admisión reprocesaba todos los aspirantes y duplicaba perfiles | Solo procesa los cambios, en una transacción, con control optimista; aceptar es definitivo |
| Swing atado a NetBeans, un `main` por ventana, SQL concatenado | JavaFX + Maven, una sola ventana, solo `PreparedStatement` |
| Cambiar de grupo borraba las calificaciones | Se conservan (cada una ligada al grupo donde se cursó) |
| Un profesor "habilitado" veía todos los grupos de la materia | Solo ve las clases que imparte (`group_subject.id_teacher`) |
| Portal PHP con rutas rotas, datos fijos en JS y `localStorage` | Next.js con datos reales de la base |
| El estatus de aspirante se consultaba solo con la CURP | Con CURP **y** folio `ASP-000123` |
| Contraseñas de 4 caracteres; cambiarla no pedía la actual | 8 caracteres mínimo; cambiarla exige la actual |
| Credenciales de la base en el repositorio | `.env` ignorado por git (plantilla `.env.example`) |
| Sin pruebas | Más de 200 pruebas automáticas contra una base real, incluido un flujo completo entre las dos apps |
