# Cómo contribuir

Gracias por el interés. BISUNI empezó como proyecto escolar; los issues y los pull requests son bienvenidos.

1. Abre un issue antes de un cambio grande, para acordar el enfoque.
2. Prepara el entorno con [docs/desarrollo.md](docs/desarrollo.md).
3. Antes del PR, todo debe pasar:
   ```bash
   cd desktop && ./mvnw verify
   cd web && npm run typecheck && npm run lint && npm test && npm run test:e2e
   ```
   El CI de GitHub corre lo mismo.

## Reglas del proyecto
- **Esquema:** `db/migrations` es la única fuente de verdad. Nunca edites una migración ya publicada; agrega
  `V2__...sql`, `V3__...sql`, etc. Después corre `npm run db:pull -- --embebida` en `web/`. Si la migración agrega
  restricciones, añade su mensaje en `desktop/.../service/SqlErrors.java`.
- **Idioma:** la interfaz, los mensajes de error y la documentación van en español. El código (nombres de clases,
  tablas y columnas) va en inglés.
- **Pruebas:** cada regla nueva lleva su prueba contra la base real (PostgreSQL embebido). Cada vista nueva del
  escritorio se agrega a `ViewsSmokeTest`.
- **Commits:** mensaje corto en español que diga qué cambia, p. ej. `Escritorio: filtro de periodo en Grupos`.
