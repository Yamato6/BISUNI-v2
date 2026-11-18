# Base de datos de BISUNI

PostgreSQL 17 o superior. Las dos aplicaciones (escritorio y web) usan esta misma base.

| Archivo | Qué es |
|---|---|
| `migrations/V*__*.sql` | El esquema, versionado con [Flyway](https://flywaydb.org). Es la única fuente de verdad. |
| `seed/R__seed_dev.sql` | Datos de demostración: periodos, carreras, grupos, alumnos con historial y las cuentas de prueba. **Empieza con `TRUNCATE`**: nunca lo cargues en una base con datos reales. |
| `local-setup.sql` | Crea el usuario y la base `bisuni` en un PostgreSQL instalado (lo corre el superusuario `postgres`). |
| `primer-admin.sql` | Crea el primer administrador en una base **sin** datos de demostración. |

## Instalar

### 1. Crear el usuario y la base
Con el superusuario `postgres`. Elige una contraseña para el usuario `bisuni`:

```bash
psql -h localhost -U postgres -d postgres -v user=bisuni -v db=bisuni -v pass=LA-CONTRASEÑA -f local-setup.sql
```

### 2. Aplicar el esquema
Elige una de estas formas.

**Con Flyway**: es lo recomendado y la forma de aplicar las migraciones futuras.

```bash
# Solo el esquema (instalación real):
flyway -url=jdbc:postgresql://localhost:5432/bisuni -user=bisuni -password=LA-CONTRASEÑA \
       -locations=filesystem:migrations migrate
# Esquema + datos de demostración:
flyway ... -locations=filesystem:migrations,filesystem:seed migrate
```

En el repositorio, `docker compose up -d` hace lo mismo con la imagen de Flyway. Desde el código, también sirve
`cd desktop && ./mvnw exec:java -Dexec.mainClass=mx.bisuni.desktop.dev.DbMigrate`.

**Con psql**: sirve para una instalación nueva. Las migraciones posteriores habrá que aplicarlas en orden y a mano.

```bash
psql -h localhost -U bisuni -d bisuni -1 -f migrations/V1__schema.sql
psql -h localhost -U bisuni -d bisuni -1 -f seed/R__seed_dev.sql      # opcional: datos de demostración
```

### 3. Primer administrador (solo sin datos de demostración)
Este paso se corre solo si **no** cargaste los datos de demostración.

```bash
psql -h localhost -U bisuni -d bisuni -v email=direccion@escuela.edu -v nombre=Ana -v apellido=López \
     -v pass=temporal-2026 -f primer-admin.sql
```

- Esa contraseña es temporal: el escritorio pide cambiarla al entrar.
- Los demás usuarios (administradores, profesores y alumnos) se crean desde el escritorio.
- En Windows, `psql` puede perder los acentos de los argumentos. Si pasa, corrige el nombre desde el escritorio, en Usuarios.

Con los datos de demostración, todas las cuentas usan la contraseña `password123` (por ejemplo,
`admin.master@bisuni.edu`).

## Reglas
- **No se edita una migración ya aplicada**: Flyway guarda su checksum. Cada cambio va en una migración nueva
  (`V2__descripcion.sql`, `V3__...`).
- Las reglas del negocio que no deben romperse (periodos, actas, asistencia, bitácora…) viven en la base como
  restricciones y triggers. Se describen en [docs/base-de-datos.md](../docs/base-de-datos.md).
