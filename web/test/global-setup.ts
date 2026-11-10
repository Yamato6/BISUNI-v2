import type { TestProject } from "vitest/node";
import { startTestDatabase } from "../scripts/test-db.mjs";

/**
 * Levanta un PostgreSQL 17 embebido con las migraciones y el seed reales en la base plantilla
 * `bisuni_template`. Cada archivo de prueba la clona (test/setup.ts), así que las pruebas pueden
 * modificar datos sin afectarse entre sí.
 */
declare module "vitest" {
  export interface ProvidedContext {
    pgServerUrl: string;
  }
}

export default async function setup(project: TestProject) {
  const port = 55000 + Math.floor(Math.random() * 5000);
  const db = await startTestDatabase({ port, database: "bisuni_template" });
  project.provide("pgServerUrl", db.serverUrl);
  return db.stop;
}
