import { expect, test, type Page } from "@playwright/test";
import { desktop } from "./desktop";

/**
 * Fase 4: flujo completo entre las dos aplicaciones, con el código real de cada una.
 *   web        → un aspirante se registra
 *   escritorio → el admin lo acepta (AdmissionService) y lo asigna a un grupo (GroupService)
 *   escritorio → la profesora del grupo le captura calificaciones (GradeService)
 *   web        → el alumno ve su grupo, horario, calificaciones y profesores
 * Seed: BA-1A (Business Administration, 1er semestre) con Introduction to Business (materia 9) y
 * Basic Accounting (10), ambas de Silvia Torres, que también es la tutora del grupo.
 */
test.describe.configure({ timeout: 300_000 }); // incluye compilar y arrancar el escritorio

const ADMIN = ["admin.master@bisuni.edu", "password123"] as const;
const TEACHER = ["silvia.torres@bisuni.edu", "password123"] as const;
const ASPIRANT = {
  firstName: "Lucía",
  lastName: "Mendoza Ríos",
  curp: "MERL060215MASNSC03",
  email: "lucia.mendoza@correo.com",
  password: "lucia2025",
};

async function login(page: Page) {
  await page.goto("/login");
  await page.getByLabel("Correo institucional").fill(ASPIRANT.email);
  await page.getByLabel("Contraseña").fill(ASPIRANT.password);
  await page.getByRole("button", { name: "Entrar" }).click();
}

test("registro web → aceptación y grupo en escritorio → calificaciones del profesor → el alumno las ve", async ({ page }) => {
  // 1. WEB: registro del aspirante
  await page.goto("/aspirantes/registro");
  await page.getByLabel("Nombre(s)").fill(ASPIRANT.firstName);
  await page.getByLabel("Apellidos").fill(ASPIRANT.lastName);
  await page.getByLabel("CURP").fill(ASPIRANT.curp);
  await page.getByLabel("Correo electrónico").fill(ASPIRANT.email);
  await page.getByLabel("Contraseña", { exact: true }).fill(ASPIRANT.password);
  await page.getByLabel("Confirmar contraseña").fill(ASPIRANT.password);
  await page.getByLabel("Carrera de interés").selectOption({ label: "Business Administration" });
  await page.getByRole("button", { name: "Enviar solicitud" }).click();
  const folio = (await page.getByTestId("folio").textContent())!.trim();

  // Todavía pendiente: no puede entrar al portal.
  await login(page);
  await expect(page.locator("form").getByRole("alert")).toContainText("todavía no ha sido aceptada");

  // 2. ESCRITORIO: el admin lo acepta y lo asigna a BA-1A
  desktop("accept", ...ADMIN, folio);
  desktop("assign-group", ...ADMIN, ASPIRANT.email, "BA-1A");

  // Aceptarlo dos veces no duplica nada (la v1 sí duplicaba student_profile).
  expect(() => desktop("accept", ...ADMIN, folio)).toThrow(/modificado por otra persona|no aplicó/);

  // 3. ESCRITORIO: la profesora captura parciales (uno queda pendiente en la otra materia)
  desktop("grade", ...TEACHER, ASPIRANT.email, "BA-1A", "9", "9.5", "8", "9");
  desktop("grade", ...TEACHER, ASPIRANT.email, "BA-1A", "10", "7", "6.5", "0");

  // 4. WEB: el alumno entra y ve todo
  await login(page);
  await expect(page).toHaveURL(/\/dashboard$/);
  await expect(page.getByRole("heading", { name: `¡Hola, ${ASPIRANT.firstName}!` })).toBeVisible();
  await expect(page.getByText("Business Administration · 1° semestre · Grupo BA-1A")).toBeVisible();

  await page.getByRole("link", { name: "Calificaciones", exact: true }).click();
  const intro = page.getByRole("row", { name: /Introduction to Business/ });
  await expect(intro).toContainText("8.83"); // (9.5 + 8 + 9) / 3, calculada por la base
  await expect(intro).toContainText("Aprobada");
  const accounting = page.getByRole("row", { name: /Basic Accounting/ });
  await expect(accounting).toContainText("4.50");
  await expect(accounting).toContainText("Reprobada");
  await expect(accounting).toContainText("Silvia Torres");

  await page.getByRole("link", { name: "Horario", exact: true }).click();
  await expect(page.getByText(/Grupo BA-1A · \d+ clases por semana/)).toBeVisible();

  await page.getByRole("link", { name: "Mis profesores" }).click();
  await expect(page.getByRole("heading", { name: "Silvia Torres" })).toBeVisible();
  await expect(page.getByText("Tutor del grupo")).toBeVisible();

  // La solicitud aparece aceptada con su fecha de inscripción.
  await page.getByRole("button", { name: "Cerrar sesión" }).click();
  await expect(page).toHaveURL("/");
  await page.goto("/aspirantes/estatus");
  await page.getByLabel("CURP").fill(ASPIRANT.curp);
  await page.getByLabel("Folio").fill(folio);
  await page.getByRole("button", { name: "Consultar" }).click();
  await expect(page.getByTestId("status")).toHaveText("Aceptado");
  await expect(page.getByText("Fecha de inscripción")).toBeVisible();
});
