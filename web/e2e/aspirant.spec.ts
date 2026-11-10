import { expect, test, type Page } from "@playwright/test";
import postgres from "postgres";

// Base embebida que levanta scripts/e2e-server.mjs (nunca la de desarrollo).
const E2E_DB = "postgres://postgres:postgres@localhost:54329/bisuni_e2e";

const CURP = "GOMA050101HASRRL09";
const EMAIL = "ana.gomez@correo.com";
const PASSWORD = "segura123";

/** Lo mismo que hace AdmissionService (escritorio) al aceptar: una transacción con rol, perfil e inscripción. */
async function acceptAsAdmin(folio: string) {
  const idAspirant = Number(folio.replace("ASP-", ""));
  const sql = postgres(E2E_DB, { max: 1, onnotice: () => {} });
  await sql.begin(async (tx) => {
    const [a] = await tx`
      UPDATE aspirant_profile SET status = 'Accepted'
      WHERE id_aspirant = ${idAspirant} AND status = 'Pending'
      RETURNING id_user, id_career`;
    await tx`UPDATE users SET role = 'Student' WHERE id_user = ${a.id_user}`;
    const [s] = await tx`
      INSERT INTO student_profile (id_user, id_career) VALUES (${a.id_user}, ${a.id_career}) RETURNING id_student`;
    await tx`INSERT INTO enrollment (id_aspirant, id_student, approved_by) VALUES (${idAspirant}, ${s.id_student}, 1)`;
  });
  await sql.end();
}

async function checkStatus(page: Page, folio: string) {
  await page.goto("/aspirantes/estatus");
  await page.getByLabel("CURP").fill(CURP.toLowerCase());
  await page.getByLabel("Folio").fill(folio);
  await page.getByRole("button", { name: "Consultar" }).click();
}

test("registro con datos inválidos marca los campos y conserva lo capturado", async ({ page }) => {
  await page.goto("/aspirantes/registro");
  await page.getByLabel("Nombre(s)").fill("Ana");
  await page.getByLabel("CURP").fill("CURP-MALA");
  await page.getByLabel("Contraseña", { exact: true }).fill("corta");
  await page.getByRole("button", { name: "Enviar solicitud" }).click();
  await expect(page.locator("#curp-error")).toBeVisible();
  await expect(page.locator("#password-error")).toContainText("al menos 8");
  await expect(page.locator("#idCareer-error")).toBeVisible();
  await expect(page.getByLabel("Nombre(s)")).toHaveValue("Ana");
});

test("estatus con CURP y folio que no coinciden", async ({ page }) => {
  await checkStatus(page, "ASP-000002"); // folio de otra persona
  // Next agrega su propio role="alert" vacío (anunciador de rutas); se filtra por texto.
  await expect(page.getByRole("alert").filter({ hasText: "No encontramos" })).toContainText("No encontramos una solicitud");
});

test("flujo completo: registro → estatus pendiente → aceptación → alumno", async ({ page }) => {
  // 1. Registro en la web
  await page.goto("/aspirantes/registro");
  await page.getByLabel("Nombre(s)").fill("Ana");
  await page.getByLabel("Apellidos").fill("Gómez Ruiz");
  await page.getByLabel("CURP").fill(CURP);
  await page.getByLabel("Correo electrónico").fill(EMAIL);
  await page.getByLabel("Contraseña", { exact: true }).fill(PASSWORD);
  await page.getByLabel("Confirmar contraseña").fill(PASSWORD);
  await page.getByLabel("Carrera de interés").selectOption({ label: "Business Administration" });
  await page.getByRole("button", { name: "Enviar solicitud" }).click();
  const folio = (await page.getByTestId("folio").textContent())!.trim();
  expect(folio).toMatch(/^ASP-\d{6}$/);

  // 2. Estatus pendiente; todavía no entra al portal
  await checkStatus(page, folio);
  await expect(page.getByTestId("status")).toHaveText("En revisión");

  // 3. Control escolar lo acepta (escritorio)
  await acceptAsAdmin(folio);

  // 4. Estatus aceptado y entra como alumno, todavía sin grupo
  await checkStatus(page, folio);
  await expect(page.getByTestId("status")).toHaveText("Aceptado");
  await page.getByRole("link", { name: "Ir al portal de alumnos" }).click();
  await page.getByLabel("Correo institucional").fill(EMAIL);
  await page.getByLabel("Contraseña").fill(PASSWORD);
  await page.getByRole("button", { name: "Entrar" }).click();
  await expect(page).toHaveURL(/\/dashboard$/);
  await expect(page.getByRole("heading", { name: "¡Hola, Ana!" })).toBeVisible();
  await expect(page.getByText("Todavía no tienes grupo asignado")).toBeVisible();
});
