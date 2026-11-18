import { execSync } from "node:child_process";
import path from "node:path";

/**
 * Ejecuta acciones del ESCRITORIO con sus servicios reales (desktop/.../dev/DesktopCli.java)
 * contra la misma base embebida de las pruebas E2E. Requiere Java 21+ (usa el Maven Wrapper).
 */
const DESKTOP_DIR = path.resolve(__dirname, "../../desktop");
// Ruta absoluta y entre comillas: cmd.exe no siempre busca en el directorio actual.
const MVNW = `"${path.join(DESKTOP_DIR, process.platform === "win32" ? "mvnw.cmd" : "mvnw")}"`;

/**
 * Corre uno o varios comandos del escritorio en una sola JVM (arrancar Maven tarda). Cada comando es
 * un arreglo `[nombre, ...argumentos]`; los argumentos no llevan espacios (en textos, `_` = espacio).
 * Devuelve la línea `OK ...` de cada comando; lanza si alguno fue rechazado.
 */
export function desktop(...commands: string[][]): string[] {
  const args = commands.map((c) => c.join(" ")).join(" + ");
  const command = [
    MVNW,
    "-q",
    "-B",
    "test-compile",
    "exec:java",
    '"-Dexec.mainClass=mx.bisuni.desktop.dev.DesktopCli"',
    `"-Dexec.args=${args}"`,
  ].join(" ");
  let output: string;
  try {
    output = execSync(command, {
      cwd: DESKTOP_DIR,
      encoding: "utf8",
      timeout: 240_000,
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
    const rejected = (e.stdout ?? "").split(/\r?\n/).find((l) => l.startsWith("RECHAZADO "));
    throw new Error(rejected ?? `Falló el escritorio (${commands[0][0]}):\n${e.stdout ?? ""}\n${e.stderr ?? ""}`.slice(0, 4000));
  }
  const ok = output.split(/\r?\n/).filter((l) => l.startsWith("OK "));
  if (ok.length !== commands.length) {
    throw new Error(`El escritorio no aplicó todos los comandos:\n${output}`);
  }
  return ok;
}
