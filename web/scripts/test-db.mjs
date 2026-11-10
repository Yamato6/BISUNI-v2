// PostgreSQL 17 embebido para pruebas (Vitest y Playwright), sin Docker.
// Aplica las migraciones y el seed REALES del repositorio (../db) en la base indicada.
import { mkdtempSync, readdirSync, readFileSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import path from "node:path";
import EmbeddedPostgres from "embedded-postgres";
import postgres from "postgres";

const USER = "postgres";
const PASSWORD = "postgres";
const REPO_DB = path.resolve(import.meta.dirname, "../../db");

function sqlFiles(dir, prefix) {
  return readdirSync(dir)
    .filter((f) => f.startsWith(prefix) && f.endsWith(".sql"))
    .sort((a, b) => a.localeCompare(b, undefined, { numeric: true }))
    .map((f) => path.join(dir, f));
}

/**
 * @param {{ port: number, database: string }} options
 * @returns {Promise<{ serverUrl: string, url: string, stop: () => Promise<void> }>}
 */
export async function startTestDatabase({ port, database }) {
  const dataDir = mkdtempSync(path.join(tmpdir(), "bisuni-web-test-"));
  const pg = new EmbeddedPostgres({ databaseDir: dataDir, user: USER, password: PASSWORD, port, persistent: false, onLog: () => {} });
  await pg.initialise();
  await pg.start();
  await pg.createDatabase(database);

  const serverUrl = `postgres://${USER}:${PASSWORD}@localhost:${port}`;
  const sql = postgres(`${serverUrl}/${database}`, { max: 1, onnotice: () => {} });
  const files = [...sqlFiles(path.join(REPO_DB, "migrations"), "V"), ...sqlFiles(path.join(REPO_DB, "seed"), "R__")];
  for (const file of files) {
    await sql.unsafe(readFileSync(file, "utf8"));
  }
  await sql.end();

  return {
    serverUrl,
    url: `${serverUrl}/${database}`,
    stop: async () => {
      await pg.stop();
      rmSync(dataDir, { recursive: true, force: true });
    },
  };
}
