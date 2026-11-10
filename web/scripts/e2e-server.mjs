// Servidor para las pruebas E2E (Playwright `webServer`): BD embebida recién sembrada + `next start`.
// Así las pruebas nunca tocan la base de desarrollo. Requiere un `next build` previo.
import { spawn } from "node:child_process";
import path from "node:path";
import { startTestDatabase } from "./test-db.mjs";

const PORT = process.env.E2E_PORT ?? "3100";
const db = await startTestDatabase({ port: 54329, database: "bisuni_e2e" });

const next = spawn(process.execPath, [path.resolve(import.meta.dirname, "../node_modules/next/dist/bin/next"), "start", "-p", PORT], {
  cwd: path.resolve(import.meta.dirname, ".."),
  stdio: "inherit",
  env: { ...process.env, DATABASE_URL: db.url, AUTH_URL: `http://localhost:${PORT}` },
});

let stopping = false;
async function shutdown(code = 0) {
  if (stopping) return;
  stopping = true;
  next.kill();
  await db.stop().catch(() => {});
  process.exit(code);
}
next.on("exit", (code) => shutdown(code ?? 0));
for (const signal of ["SIGINT", "SIGTERM"]) process.on(signal, () => shutdown(0));
