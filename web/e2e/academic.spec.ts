import { expect, test, type Page } from "@playwright/test";
import postgres from "postgres";

// Base embebida que levanta scripts/e2e-server.mjs (nunca la de desarrollo).
const E2E_DB = "postgres://postgres:postgres@localhost:54329/bisuni_e2e";

async function login(page: Page, email: string, password = "password123") {
  await page.goto("/login");
  await page.getByLabel("Correo institucional").fill(email);
  await page.getByLabel("Contraseña").fill(password);
  await page.getByRole("button", { name: "Entrar" }).click();
}

/*
 * Seed: student051 (generación 2026-1) cursó IT-1A en 2026-1 y hoy está en IT-2A;
 * student053 está en riesgo por faltas y debe Discrete Mathematics; student060 tiene baja definitiva.
 */
test("inicio: avisos, alertas de riesgo y avance de carrera", async ({ page }) => {
  await login(page, "student053@bisuni.edu");
  await expect(page).toHaveURL(/\/dashboard$/);
  await expect(page.getByText("Semana de Tecnologías de la Información")).toBeVisible(); // aviso para IT
  await expect(page.getByText("Aviso vencido")).toHaveCount(0);
  await expect(page.getByText(/Llevas \d+ faltas en Database Basics/)).toBeVisible();
  await expect(page.getByText(/Debes la materia Discrete Mathematics/)).toBeVisible();
});

test("kárdex con historial de dos periodos y descarga de documentos PDF", async ({ page }) => {
  await login(page, "student051@bisuni.edu");
  await page.getByRole("link", { name: "Kárdex", exact: true }).click();
  await expect(page.getByRole("heading", { name: /Periodo 2026-1 · IT-1A/ })).toBeVisible();
  await expect(page.getByRole("heading", { name: /Periodo 2026-2 · IT-2A/ })).toBeVisible();
  await expect(page.getByText("8 de 32 créditos")).toBeVisible();

  await page.getByRole("link", { name: "Documentos", exact: true }).click();
  for (const path of ["/documentos/constancia", "/documentos/kardex", "/documentos/boleta"]) {
    const res = await page.request.get(path);
    expect(res.status(), path).toBe(200);
    expect(res.headers()["content-type"]).toBe("application/pdf");
    expect((await res.body()).subarray(0, 5).toString()).toBe("%PDF-");
  }
  const download = page.waitForEvent("download");
  await page.getByTestId("constancia").click();
  expect((await download).suggestedFilename()).toBe("constancia-2026IT0001.pdf");
});

test("asistencia por materia con fechas de faltas", async ({ page }) => {
  await login(page, "student053@bisuni.edu");
  await page.getByRole("link", { name: "Asistencia", exact: true }).click();
  const card = page.getByRole("region", { name: "Database Basics" });
  await expect(card).toContainText("35.4 %");
  await expect(card).toContainText("En riesgo por faltas");
  await card.getByText(/Ver fechas/).click();
  await expect(card.getByText(/· Falta/).first()).toBeVisible();
});

test("los documentos exigen sesión", async ({ request }) => {
  const res = await request.get("/documentos/kardex", { maxRedirects: 0 });
  expect([302, 303, 307]).toContain(res.status());
});

test("contraseña restablecida: hay que crear una propia antes de entrar", async ({ page }) => {
  const sql = postgres(E2E_DB, { max: 1, onnotice: () => {} });
  await sql`UPDATE users SET must_change_password = true WHERE email = 'student004@bisuni.edu'`;
  await sql.end();

  await login(page, "student004@bisuni.edu");
  await expect(page).toHaveURL(/\/cambiar-contrasena$/);
  await page.goto("/calificaciones"); // cualquier página del portal regresa aquí
  await expect(page).toHaveURL(/\/cambiar-contrasena$/);

  await page.getByLabel("Nueva contraseña", { exact: true }).fill("password123");
  await page.getByLabel("Confirmar nueva contraseña").fill("password123");
  await page.getByRole("button", { name: "Crear mi contraseña" }).click();
  await expect(page.locator("#next-error")).toHaveText("La nueva contraseña debe ser distinta de la temporal.");

  await page.getByLabel("Nueva contraseña", { exact: true }).fill("valeria-2026");
  await page.getByLabel("Confirmar nueva contraseña").fill("valeria-2026");
  await page.getByRole("button", { name: "Crear mi contraseña" }).click();
  await expect(page).toHaveURL(/\/dashboard\?contrasena=nueva$/);
  await expect(page.getByText("Listo: tu contraseña se cambió.")).toBeVisible();
});

test("un alumno con baja definitiva no entra", async ({ page }) => {
  await login(page, "student060@bisuni.edu");
  await expect(page.locator("form").getByRole("alert")).toContainText("baja definitiva");
});
