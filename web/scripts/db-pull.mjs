// Regenera src/db/generated/{schema,relations}.ts desde la base de datos (drizzle-kit pull)
// y borra la migración y los metadatos que drizzle-kit crea de paso: en este proyecto las
// migraciones son SOLO las de Flyway (../db/migrations).
//
//   npm run db:pull                 usa la base del .env (DATABASE_URL), ya migrada
//   npm run db:pull -- --embebida   levanta un PostgreSQL temporal con las migraciones del repo
//                                   (no depende de cómo esté tu base local)
import { execSync } from "node:child_process";
import { readdirSync, rmSync } from "node:fs";
import path from "node:path";
import { startTestDatabase } from "./test-db.mjs";

const root = path.resolve(import.meta.dirname, "..");
const out = path.resolve(root, "src/db/generated");
const embedded = process.argv.includes("--embebida");

let db;
const env = { ...process.env };
if (embedded) {
  db = await startTestDatabase({ port: 54331, database: "bisuni_pull" });
  env.DATABASE_URL = db.url;
  console.log("Base temporal con las migraciones del repo:", db.url);
}
try {
  execSync("npx drizzle-kit pull", { stdio: "inherit", cwd: root, env });
} finally {
  await db?.stop();
}
for (const f of readdirSync(out)) {
  if (f.endsWith(".sql") || f === "meta") {
    rmSync(path.join(out, f), { recursive: true, force: true });
  }
}
console.log("Esquema regenerado en src/db/generated (sin migraciones).");
