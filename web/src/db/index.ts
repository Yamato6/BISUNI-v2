import "server-only";
import { drizzle } from "drizzle-orm/postgres-js";
import postgres from "postgres";
import * as schema from "./generated/schema";
import * as relations from "./generated/relations";

/**
 * Conexión única a PostgreSQL. El esquema de Drizzle se genera con `npm run db:pull`
 * a partir de las migraciones de Flyway; aquí nunca se definen tablas a mano.
 */
function createDb() {
  const url = process.env.DATABASE_URL;
  if (!url) {
    throw new Error("Falta DATABASE_URL (defínela en el .env de la raíz del repositorio).");
  }
  const client = postgres(url, { max: 10, prepare: true });
  return drizzle(client, { schema: { ...schema, ...relations } });
}

type Db = ReturnType<typeof createDb>;

// En desarrollo, Next recarga módulos; se reutiliza la conexión para no abrir un pool por recarga.
const globalForDb = globalThis as unknown as { bisuniDb?: Db };

export function db(): Db {
  globalForDb.bisuniDb ??= createDb();
  return globalForDb.bisuniDb;
}

export { schema };
