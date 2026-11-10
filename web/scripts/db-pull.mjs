// Regenera src/db/generated/{schema,relations}.ts desde la base de datos (drizzle-kit pull)
// y borra la migración y los metadatos que drizzle-kit crea de paso: en este proyecto las
// migraciones son SOLO las de Flyway (../db/migrations).
import { execSync } from "node:child_process";
import { readdirSync, rmSync } from "node:fs";
import path from "node:path";

const out = path.resolve(import.meta.dirname, "../src/db/generated");
execSync("npx drizzle-kit pull", { stdio: "inherit", cwd: path.resolve(import.meta.dirname, "..") });
for (const f of readdirSync(out)) {
  if (f.endsWith(".sql") || f === "meta") {
    rmSync(path.join(out, f), { recursive: true, force: true });
  }
}
console.log("Esquema regenerado en src/db/generated (sin migraciones).");
