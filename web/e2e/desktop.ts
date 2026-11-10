import { execSync } from "node:child_process";
import path from "node:path";

/**
 * Ejecuta una acción del ESCRITORIO con sus servicios reales (desktop/.../dev/DesktopCli.java)
 * contra la misma base embebida de las pruebas E2E. Requiere Java 21+ (usa el Maven Wrapper).
 */
const DESKTOP_DIR = path.resolve(__dirname, "../../desktop");
// Ruta absoluta y entre comillas: cmd.exe no siempre busca en el directorio actual.
const MVNW = `"${path.join(DESKTOP_DIR, process.platform === "win32" ? "mvnw.cmd" : "mvnw")}"`;

export function desktop(...args: string[]): string {
  const command = [
    MVNW,
    "-q",
    "-B",
    "test-compile",
    "exec:java",
    '"-Dexec.mainClass=mx.bisuni.desktop.dev.DesktopCli"',
    `"-Dexec.args=${args.join(" ")}"`,
  ].join(" ");
  let output: string;
  try {
    output = execSync(command, {
    cwd: DESKTOP_DIR,
    encoding: "utf8",
    timeout: 180_000,
    // La conexión del escritorio sale de las variables DB_* (tienen prioridad sobre el .env).
    env: {
      ...process.env,
      DB_HOST: "localhost",
      DB_PORT: "54329",
      DB_NAME: "bisuni_e2e",
      DB_USER: "postgres",
      DB_PASSWORD: "postgres",
    },
    });
  } catch (error) {
    const e = error as { stdout?: string; stderr?: string };
    throw new Error(`Falló el escritorio (${args[0]}):
${e.stdout ?? ""}
${e.stderr ?? ""}`.slice(0, 4000));
  }
  const line = output.split(/\r?\n/).find((l) => l.startsWith("OK ") || l.startsWith("RECHAZADO "));
  if (!line?.startsWith("OK ")) {
    throw new Error(`El escritorio no aplicó "${args[0]}": ${line ?? output}`);
  }
  return line;
}
