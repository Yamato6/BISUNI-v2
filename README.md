# BISUNI v2

Sistema de control escolar universitario. Es la segunda versión del Proyecto Integrador I (TDSM3A, sep–dic 2025).

| Carpeta | Qué es | Usuarios |
|---|---|---|
| `db/` | Esquema PostgreSQL y datos de prueba, aplicados con Flyway | — |
| `desktop/` | App de escritorio JavaFX + Maven *(en construcción: login y admisiones listos)* | Administradores y profesores |
| `web/` | Portal Next.js + TypeScript *(en construcción)* | Alumnos y aspirantes |

Las dos aplicaciones se conectan a la misma base de datos.

## Cómo arrancar la base de datos

Requisitos: Docker con Compose.

```bash
cp .env.example .env      # pon una contraseña en DB_PASSWORD
docker compose up -d      # levanta Postgres; el servicio flyway aplica esquema + seed y termina
docker compose logs flyway
```

### Sin Docker
La app de escritorio trae una BD de desarrollo embebida (PostgreSQL 17). Solo necesita Java 21 o superior:

```bash
cp .env.example .env      # pon una contraseña en DB_PASSWORD
cd desktop
./mvnw exec:java          # aplica esquema + seed y se queda corriendo (Ctrl+C para detener)
# ./mvnw exec:java -Dexec.args=--reset    # la borra y la recrea desde cero
```

## Cómo arrancar el escritorio (`desktop/`)

Requisitos: Java 21 o superior. Maven no hace falta, porque se usa el wrapper `mvnw`.

```bash
cd desktop
./mvnw verify             # compila y corre las pruebas (usan su propio Postgres embebido)
./mvnw javafx:run         # abre la app; toma la conexión de ../.env
```

Para usar otra conexión distinta a la del `.env`, copia `desktop/bisuni.properties.example` a `desktop/bisuni.properties` o define las variables de entorno `DB_*`.

### Cuentas de prueba
Todas usan la contraseña `password123`.

| Rol | Email |
|---|---|
| Admin | `admin.master@bisuni.edu` |
| Profesor | `juan.perez@bisuni.edu` |
| Alumno (con grupo y calificaciones) | `student001@bisuni.edu` |
| Aspirante pendiente | CURP `BEXA070312MASLND01` |

## La base de datos (`db/`)

- `db/migrations/V*__*.sql` es el esquema y la única fuente de verdad.
  - Nunca edites una migración que ya se aplicó; crea una nueva `V2__...sql`.
- `db/seed/R__seed_dev.sql` contiene datos de prueba, solo para desarrollo.
  - Flyway lo vuelve a ejecutar cuando cambia el archivo.

### Cambios respecto a v1 (MySQL)

| v1 | v2 |
|---|---|
| Contraseñas en texto plano | `password_hash` en bcrypt; un `CHECK` rechaza texto plano |
| `group_student` + `student_profile.id_group` (se desincronizaban) | Solo `student_profile.id_group`, con FK que exige que alumno y grupo sean de la misma carrera |
| `schedule_group` + `schedule_teacher` duplicados y sin detectar choques | Una tabla `schedule` con `UNIQUE` por grupo y por profesor; el horario del profesor es la vista `v_teacher_schedule` |
| No se sabía qué profesor da cada materia a cada grupo | `group_subject.id_teacher`, validado contra `teacher_subject` |
| `final_grade` calculado a mano | Columna generada: promedio de los 3 parciales |
| Se podían duplicar calificaciones | `UNIQUE (id_student, id_subject, id_group)`; las apps capturan con `INSERT ... ON CONFLICT` |
| `enrollment` nunca se llenaba | `UNIQUE` en aspirante y en alumno; lo llena el proceso de admisión |
| CURP sin validar | `CHAR(18)` única y con formato validado |
| Calificaciones y GPA sin límites (`DECIMAL(3,2)` ni siquiera admitía 10) | `NUMERIC(4,2)` con `CHECK` entre 0 y 10 |
