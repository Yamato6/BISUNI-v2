import postgres from "postgres";
import { afterAll, beforeAll, inject } from "vitest";

/**
 * Antes de cada archivo de prueba: clona la base plantilla (esquema + seed) en una base propia y
 * apunta DATABASE_URL a ella. src/db/index.ts crea la conexión de forma perezosa, así que basta
 * con fijar la variable y limpiar la conexión cacheada.
 */
const server = inject("pgServerUrl");
const name = `bisuni_t_${process.pid}_${Math.random().toString(36).slice(2, 8)}`;

beforeAll(async () => {
  const admin = postgres(`${server}/postgres`, { max: 1, onnotice: () => {} });
  await admin.unsafe(`CREATE DATABASE ${name} TEMPLATE bisuni_template`);
  await admin.end();
  process.env.DATABASE_URL = `${server}/${name}`;
  (globalThis as { bisuniDb?: unknown }).bisuniDb = undefined;
});

afterAll(async () => {
  const g = globalThis as { bisuniDb?: { $client?: { end: () => Promise<void> } } };
  await g.bisuniDb?.$client?.end();
  g.bisuniDb = undefined;
});
