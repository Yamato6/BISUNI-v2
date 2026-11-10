import path from "node:path";
import { defineConfig } from "vitest/config";

export default defineConfig({
  resolve: {
    alias: {
      "@": path.resolve(import.meta.dirname, "src"),
      // `server-only` lanza un error fuera del bundle de servidor de Next; en pruebas no aplica.
      "server-only": path.resolve(import.meta.dirname, "test/server-only-stub.ts"),
    },
  },
  test: {
    environment: "node",
    include: ["src/**/*.test.ts", "test/**/*.test.ts"],
    globalSetup: ["test/global-setup.ts"],
    setupFiles: ["test/setup.ts"],
    testTimeout: 20_000,
    hookTimeout: 120_000,
  },
});
