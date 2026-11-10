import { defineConfig, devices } from "@playwright/test";

const PORT = 3100;

export default defineConfig({
  testDir: "./e2e",
  fullyParallel: false,
  workers: 1, // comparten la misma base embebida
  reporter: [["list"]],
  use: {
    baseURL: `http://localhost:${PORT}`,
    locale: "es-MX",
    trace: "retain-on-failure",
  },
  projects: [{ name: "chromium", use: { ...devices["Desktop Chrome"] } }],
  webServer: {
    command: "node scripts/e2e-server.mjs",
    url: `http://localhost:${PORT}`,
    timeout: 180_000,
    reuseExistingServer: false,
    env: { E2E_PORT: String(PORT) },
  },
});
