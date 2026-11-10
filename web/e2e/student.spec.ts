import { expect, test, type Page } from "@playwright/test";

/** Mensaje de error del formulario (Next agrega además su propio role="alert" vacío para anunciar rutas). */
function formError(page: Page) {
  return page.locator("form").getByRole("alert");
}

async function login(page: Page, email: string, password = "password123") {
  await page.goto("/login");
  await page.getByLabel("Correo institucional").fill(email);
  await page.getByLabel("Contraseña").fill(password);
  await page.getByRole("button", { name: "Entrar" }).click();
}

test("las páginas del alumno exigen sesión", async ({ page }) => {
  await page.goto("/calificaciones");
  await expect(page).toHaveURL(/\/login/);
  await expect(page.getByRole("heading", { name: "Portal de alumnos" })).toBeVisible();
});

test("credenciales incorrectas muestran un mensaje genérico", async ({ page }) => {
  await login(page, "student001@bisuni.edu", "incorrecta");
  await expect(formError(page)).toHaveText("Correo o contraseña incorrectos.");
  await expect(page.getByLabel("Correo institucional")).toHaveValue("student001@bisuni.edu");
});

test("un profesor recibe el aviso de usar la app de escritorio", async ({ page }) => {
  await login(page, "juan.perez@bisuni.edu");
  await expect(formError(page)).toContainText("El portal web es para alumnos");
});

test("un aspirante pendiente recibe el aviso de su solicitud", async ({ page }) => {
  await login(page, "aspirant01@bisuni.edu");
  await expect(formError(page)).toContainText("todavía no ha sido aceptada");
});

test("alumno: inicio, calificaciones, horario y cierre de sesión", async ({ page }) => {
  await login(page, "Student001@bisuni.edu");
  await expect(page).toHaveURL(/\/dashboard$/);
  await expect(page.getByRole("heading", { name: "¡Hola, Carlos!" })).toBeVisible();
  await expect(page.getByText("Juan Pérez")).toBeVisible(); // tutor de IT-1A

  await page.getByRole("link", { name: "Calificaciones", exact: true }).click();
  await expect(page.getByRole("heading", { name: "Mis calificaciones" })).toBeVisible();
  const row = page.getByRole("row", { name: /Programming Fundamentals/ });
  await expect(row).toContainText("8.17");
  await expect(row).toContainText("Aprobada");

  await page.getByRole("link", { name: "Horario", exact: true }).click();
  await expect(page.getByText("Grupo IT-1A · 30 clases por semana")).toBeVisible();

  // Con sesión, /login lleva al inicio del alumno.
  await page.goto("/login");
  await expect(page).toHaveURL(/\/dashboard$/);

  await page.getByRole("button", { name: "Cerrar sesión" }).click();
  await expect(page).toHaveURL("/");
  await page.goto("/dashboard");
  await expect(page).toHaveURL(/\/login/);
});
