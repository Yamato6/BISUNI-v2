# Instalación

BISUNI tiene tres piezas que comparten una base de datos:

| Pieza | Para quién | Qué necesita |
|---|---|---|
| Base de datos | — | PostgreSQL 17 o superior |
| App de escritorio | Administradores y profesores | Windows: el zip del release (trae su propio Java). Cualquier sistema: Java 21+ para compilarla |
| Portal web | Alumnos y aspirantes | Node.js 20.9 o superior |

Hay dos formas de instalarlo:
- **Demostración:** con los datos de prueba, para conocer el sistema.
- **Instalación real:** sin datos de prueba, con tu propio primer administrador.

---

## 1. Base de datos

### Opción A: Docker
Desde la raíz del repositorio:

```bash
cp .env.example .env        # define DB_PASSWORD (y la misma en DATABASE_URL)
docker compose up -d
```

Levanta PostgreSQL 17 y Flyway aplica el esquema **y los datos de demostración**. Para una instalación real,
quita `filesystem:/flyway/seed` de `FLYWAY_LOCATIONS` en `docker-compose.yml` antes del primer `up`.

### Opción B: PostgreSQL instalado
1. **Usuario y base.** Lo corre el superusuario `postgres`:
   ```bash
   psql -h localhost -U postgres -d postgres -v user=bisuni -v db=bisuni -v pass=LA-CONTRASEÑA -f db/local-setup.sql
   ```
   En Windows, `psql` está en `C:\Program Files\PostgreSQL\<versión>\bin`.

2. **Esquema.** Elige una forma:

   | Con | Comando |
   |---|---|
   | El código (Java 21+) | `cd desktop && ./mvnw exec:java "-Dexec.mainClass=mx.bisuni.desktop.dev.DbMigrate"`: lee la conexión del `.env`. Agrega `"-Dexec.args=--sin-seed"` para una instalación real |
   | Flyway | `flyway -url=jdbc:postgresql://localhost:5432/bisuni -user=bisuni -password=... -locations=filesystem:db/migrations migrate` (agrega `,filesystem:db/seed` para la demostración) |
   | psql | `psql -U bisuni -d bisuni -1 -f db/migrations/V1__schema.sql` (y `db/seed/R__seed_dev.sql` para la demostración) |

   El zip **BISUNI-BaseDeDatos** del release trae estos mismos archivos SQL.

### Opción C: embebida (solo desarrollo)
`cd desktop && ./mvnw exec:java` levanta un PostgreSQL 17 sin instalar nada, con el esquema y los datos de
demostración. Usa el puerto de `DB_PORT` y guarda los datos en `%TEMP%/bisuni-devdb` (`-Dexec.args=--reset` la
recrea). Hay que dejarlo corriendo.

### Primer administrador (solo en una instalación real)
Sin los datos de demostración no existe ninguna cuenta. Crea la primera con el dueño de la base:

```bash
psql -h localhost -U bisuni -d bisuni -v email=direccion@escuela.edu -v nombre=Ana -v apellido=López \
     -v pass=temporal-2026 -f db/primer-admin.sql
```

- Esa contraseña es temporal: el escritorio pide cambiarla al entrar.
- A partir de ahí, todo se da de alta desde el escritorio: periodos, carreras, materias, profesores y grupos.
- Los alumnos llegan por admisión o se crean en Usuarios.
- El [manual de usuario](manual-de-usuario.md) explica el orden.

---

## 2. App de escritorio

### Desde el release (Windows)
1. Descarga `BISUNI-Escritorio-<versión>-windows-x64.zip` de
   [Releases](https://github.com/Yamato6/BISUNI-v2/releases) y descomprímelo.
2. Copia `bisuni.properties.example` como `bisuni.properties`, junto a `BISUNI.exe`, y escribe la conexión:
   ```properties
   DB_HOST=localhost
   DB_PORT=5432
   DB_NAME=bisuni
   DB_USER=bisuni
   DB_PASSWORD=LA-CONTRASEÑA
   ```
3. Abre `BISUNI.exe`. No hace falta instalar Java.

### Desde el código (Windows, macOS o Linux)
```bash
cd desktop
./mvnw javafx:run
```

La conexión se lee en este orden (gana la primera que defina cada clave):
1. variables de entorno `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER` y `DB_PASSWORD`;
2. `bisuni.properties` en el directorio actual, junto al ejecutable o en `desktop/`;
3. el `.env` de la raíz del repositorio.

Para armar tú mismo la versión empaquetada: `./mvnw -Pdist -DskipTests clean package` (en Windows, con un JDK 21+).
El resultado queda en `desktop/target/dist/BISUNI`.

---

## 3. Portal web

### Configuración
En el `.env` de la **raíz** del repositorio (lo comparten las dos apps):

| Variable | Ejemplo | Para qué |
|---|---|---|
| `DATABASE_URL` | `postgres://bisuni:LA-CONTRASEÑA@localhost:5432/bisuni` | Conexión a la base |
| `AUTH_SECRET` | `npx auth secret` lo genera | Firma de las sesiones. Usa uno propio y secreto |
| `AUTH_URL` | `https://alumnos.tu-escuela.edu` | URL pública del portal (en producción) |
| `AUTH_TRUST_HOST` | `true` | Solo detrás de un proxy inverso (nginx, Caddy…) |

### Desarrollo
```bash
cd web
npm install
npm run dev          # http://localhost:3000
```

### Producción
```bash
cd web
npm ci
npm run build
npm start            # puerto 3000; usa -p para otro
```

- Sirve el portal detrás de HTTPS, con un proxy inverso o en un servicio que soporte Next.js.
- Si cambias el `.env`, reinicia el proceso.

---

## Actualizar a una versión nueva
1. Respalda la base: `pg_dump -U bisuni bisuni > respaldo.sql`.
2. Aplica las migraciones nuevas **sin el seed**. Puedes usar Flyway (con `-locations=filesystem:db/migrations`) o
   `DbMigrate` con `"-Dexec.args=--sin-seed"`. Flyway solo aplica las que falten.
3. Reemplaza la app de escritorio por la del nuevo release y actualiza la web (`git pull`, `npm ci`, `npm run build`).

> **Nunca cargues `db/seed/R__seed_dev.sql` en una base con datos reales:** empieza con `TRUNCATE` y la vacía.
