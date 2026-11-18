# Seguridad

## Reportar una vulnerabilidad
No abras un issue público. Usa el reporte privado de GitHub:
[**Security → Report a vulnerability**](https://github.com/Yamato6/BISUNI-v2/security/advisories/new).

Incluye los pasos para reproducirla, qué parte afecta (escritorio, web o base de datos) y la versión o commit.

## Antes de instalarlo en serio
- Las cuentas del seed (`db/seed/R__seed_dev.sql`, contraseña `password123`) son **solo de demostración**. En una
  instalación real no cargues el seed; crea el primer administrador con `db/primer-admin.sql`
  (ver [docs/instalacion.md](docs/instalacion.md)).
- Genera un `AUTH_SECRET` propio para la web y no reutilices la contraseña del usuario de la base.
- Sirve la web detrás de HTTPS.

## Lo que ya cuida el sistema
- Las contraseñas se guardan con bcrypt; un `CHECK` de la base rechaza texto plano.
- Todo el SQL usa parámetros (`PreparedStatement` en Java; Drizzle o `postgres` con plantillas en la web).
- Cada página y acción del alumno vuelve a revisar contra la base la sesión, el rol, la baja y la contraseña temporal
  (`requireStudent`).
- La bitácora (`audit_log`) registra quién cambió qué y nunca guarda hashes de contraseña.
