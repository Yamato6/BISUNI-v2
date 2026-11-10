import { expect, test, type Page } from "@playwright/test";

// Se usa student003 para no interferir con las pruebas que entran como student001.
const EMAIL = "student003@bisuni.edu";

async function login(page: Page, password: string) {
  await page.goto("/login");
  await page.getByLabel("Correo institucional").fill(EMAIL);
  await page.getByLabel("Contraseña").fill(password);
  await page.getByRole("button", { name: "Entrar" }).click();
}

test("mis profesores muestra a quienes dan clase al grupo", async ({ page }) => {
  await login(page, "password123");
  await page.getByRole("link", { name: "Mis profesores" }).click();
  await expect(page.getByRole("heading", { name: "Juan Pérez" })).toBeVisible();
  await expect(page.getByRole("heading", { name: "María García" })).toBeVisible();
  await expect(page.getByText("Tutor del grupo")).toHaveCount(1);
});

test("perfil: cambia el nombre y la contraseña; la nueva sirve para entrar", async ({ page }) => {
  await login(page, "password123");
  await page.getByRole("link", { name: "Mi perfil" }).click();

  await page.getByLabel("Nombre(s)").fill("Miguel Ángel");
  await page.getByRole("button", { name: "Guardar datos" }).click();
  await expect(page.getByText("Tus datos se guardaron.")).toBeVisible();
  await expect(page.locator("aside")).toContainText("Miguel Ángel Serrano");

  await page.getByLabel("Contraseña actual").fill("equivocada");
  await page.getByLabel("Nueva contraseña", { exact: true }).fill("nueva12345");
  await page.getByLabel("Confirmar nueva contraseña").fill("nueva12345");
  await page.getByRole("button", { name: "Cambiar contraseña" }).click();
  await expect(page.locator("#current-error")).toHaveText("La contraseña actual no es correcta.");

  await page.getByLabel("Contraseña actual").fill("password123");
  await page.getByLabel("Nueva contraseña", { exact: true }).fill("nueva12345");
  await page.getByLabel("Confirmar nueva contraseña").fill("nueva12345");
  await page.getByRole("button", { name: "Cambiar contraseña" }).click();
  await expect(page.getByText("Tu contraseña se cambió.")).toBeVisible();

  await page.getByRole("button", { name: "Cerrar sesión" }).click();
  await expect(page).toHaveURL("/"); // esperar a que la sesión se cierre antes de volver a /login
  await login(page, "nueva12345");
  await expect(page).toHaveURL(/\/dashboard$/);
  await expect(page.getByRole("heading", { name: "¡Hola, Miguel Ángel!" })).toBeVisible();
});
