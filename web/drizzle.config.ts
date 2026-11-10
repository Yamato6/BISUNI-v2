import path from "node:path";
import { loadEnvConfig } from "@next/env";
import { defineConfig } from "drizzle-kit";

// forceReload: Next ya cargó (y cacheó) los .env de web/; sin forzar, este llamado no lee nada.
loadEnvConfig(path.resolve(__dirname, ".."), process.env.NODE_ENV !== "production", console, true);

/**
 * Solo se usa para `drizzle-kit pull`: el esquema se GENERA desde la base de datos.
 * La única fuente de verdad son las migraciones de Flyway en ../db/migrations;
 * web/ no define migraciones propias (no usar `drizzle-kit push` ni `generate`).
 */
export default defineConfig({
  dialect: "postgresql",
  out: "./src/db/generated",
  dbCredentials: { url: process.env.DATABASE_URL! },
  tablesFilter: ["!flyway_schema_history"],
  introspect: { casing: "camel" },
});
