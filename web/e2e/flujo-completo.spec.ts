import { expect, test, type Page } from "@playwright/test";
import { desktop } from "./desktop";

/**
 * Flujo completo entre las dos aplicaciones, con el código real de cada una.
 *   web        → un aspirante se registra en la convocatoria abierta
 *   escritorio → el admin lo acepta (matrícula) y lo asigna a un grupo
 *   escritorio → la profesora captura calificaciones, cierra un acta, pasa lista y publica un aviso
 *   escritorio → el admin le restablece la contraseña
 *   web        → el alumno crea su contraseña y ve su grupo, aviso, faltas, calificaciones, kárdex,
 *                horario, profesores y su constancia en PDF
 * Seed: BA-1A (Business Administration, 1er semestre, periodo activo 2026-2) con Introduction to
 * Business (materia 9) y Basic Accounting (10), ambas de Silvia Torres, que también es la tutora.
 */
test.describe.configure({ timeout: 300_000 }); // incluye compilar y arrancar el escritorio

const ADMIN = ["admin.master@bisuni.edu", "password123"];
const TEACHER = ["silvia.torres@bisuni.edu", "password123"];
const ASPIRANT = {
  firstName: "Lucía",
  lastName: "Mendoza Ríos",
  curp: "MERL060215MASNSC03",
  email: "lucia.mendoza@correo.com",
  password: "lucia2025",
};
/** Lunes dentro del periodo 2026-2 (la asistencia no admite fechas futuras ni fuera del periodo). */
const CLASS_DAY = "2026-11-09";

async function login(page: Page, password: string) {
  await page.goto("/login");
  await page.getByLabel("Correo institucional").fill(ASPIRANT.email);
  await page.getByLabel("Contraseña").fill(password);
  await page.getByRole("button", { name: "Entrar" }).click();
}

test("registro web → admisión, calificaciones, acta, asistencia y aviso en escritorio → el alumno lo ve todo", async ({ page }) => {
  // 1. WEB: registro del aspirante en la convocatoria abierta
  await page.goto("/aspirantes/registro");
  await expect(page.getByTestId("convocatoria")).toHaveText("2026-2");
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
  await login(page, ASPIRANT.password);
  await expect(page.locator("form").getByRole("alert")).toContainText("todavía no ha sido aceptada");

  // 2. ESCRITORIO: el admin lo acepta y lo asigna a BA-1A
  desktop(["accept", ...ADMIN, folio], ["assign-group", ...ADMIN, ASPIRANT.email, "BA-1A"]);
  // Aceptarlo dos veces no duplica nada (la v1 sí duplicaba student_profile).
  expect(() => desktop(["accept", ...ADMIN, folio])).toThrow(/modificado por otra persona/);

  // 3. ESCRITORIO: la profesora califica (y cierra el acta de una materia), pasa lista y publica un aviso;
  //    el admin le restablece la contraseña al alumno.
  const out = desktop(
    ["grade", ...TEACHER, ASPIRANT.email, "BA-1A", "9", "9.5", "8", "9"],
    ["grade-rest", ...TEACHER, "BA-1A", "9", "8", "8", "8"],
    ["close-acta", ...TEACHER, "BA-1A", "9"],
    ["grade", ...TEACHER, ASPIRANT.email, "BA-1A", "10", "7", "6.5", "0"],
    ["attendance", ...TEACHER, ASPIRANT.email, "BA-1A", "10", CLASS_DAY, "Absent"],
    ["announce", ...TEACHER, "BA-1A", "Examen_de_contabilidad", "Traigan_calculadora_y_su_formulario."],
    ["reset-password", ...ADMIN, ASPIRANT.email],
  );
  const temp = out.at(-1)!.match(/temporal=(\S+)/)![1];
  // Con el acta cerrada, el escritorio ya no deja modificar esas calificaciones.
  expect(() => desktop(["grade", ...TEACHER, ASPIRANT.email, "BA-1A", "9", "10", "10", "10"])).toThrow(/acta/);

  // 4. WEB: la contraseña anterior ya no sirve; con la temporal debe crear una propia.
  await login(page, ASPIRANT.password);
  await expect(page.locator("form").getByRole("alert")).toHaveText("Correo o contraseña incorrectos.");
  await login(page, temp);
  await expect(page).toHaveURL(/\/cambiar-contrasena$/);
  await page.getByLabel("Nueva contraseña", { exact: true }).fill("lucia-nueva-2026");
  await page.getByLabel("Confirmar nueva contraseña").fill("lucia-nueva-2026");
  await page.getByRole("button", { name: "Crear mi contraseña" }).click();
  await expect(page).toHaveURL(/\/dashboard\?contrasena=nueva$/);

  // Inicio: grupo, matrícula, aviso de su profesora y alerta de faltas.
  await expect(page.getByRole("heading", { name: `¡Hola, ${ASPIRANT.firstName}!` })).toBeVisible();
  await expect(page.getByText("Business Administration · 1° semestre · Grupo BA-1A")).toBeVisible();
  await expect(page.locator("dd", { hasText: /^2026BA\d{4}$/ })).toBeVisible();
  const aviso = page.getByTestId("aviso").filter({ hasText: "Examen de contabilidad" });
  await expect(aviso).toContainText("Traigan calculadora y su formulario.");
  await expect(aviso).toContainText("Para tu grupo · Silvia Torres");
  await expect(page.getByText(/Llevas 1 falta en Basic Accounting/)).toBeVisible();

  // Calificaciones del periodo, calculadas por la base.
  await page.getByRole("link", { name: "Calificaciones", exact: true }).click();
  const intro = page.getByRole("row", { name: /Introduction to Business/ });
  await expect(intro).toContainText("8.83"); // (9.5 + 8 + 9) / 3
  await expect(intro).toContainText("Aprobada");
  const accounting = page.getByRole("row", { name: /Basic Accounting/ });
  await expect(accounting).toContainText("4.50");
  await expect(accounting).toContainText("Silvia Torres");

  // Kárdex: el acta cerrada vuelve definitiva la materia; la otra sigue en curso.
  await page.getByRole("link", { name: "Kárdex", exact: true }).click();
  await expect(page.getByRole("row", { name: /Introduction to Business/ })).toContainText("Aprobada");
  await expect(page.getByRole("row", { name: /Basic Accounting/ })).toContainText("En curso");
  await expect(page.getByText("3 de 28 créditos")).toBeVisible(); // BA suma 28 créditos; Introduction to Business vale 3

  // Asistencia, horario y profesores.
  await page.getByRole("link", { name: "Asistencia", exact: true }).click();
  await expect(page.getByRole("region", { name: "Basic Accounting" })).toContainText("1 falta de 1 clases");
  await page.getByRole("link", { name: "Horario", exact: true }).click();
  await expect(page.getByText(/Grupo BA-1A · \d+ clases por semana/)).toBeVisible();
  await page.getByRole("link", { name: "Mis profesores" }).click();
  await expect(page.getByRole("heading", { name: "Silvia Torres" })).toBeVisible();
  await expect(page.getByText("Tutor del grupo")).toBeVisible();

  // Constancia de estudios en PDF.
  const pdf = await page.request.get("/documentos/constancia");
  expect(pdf.headers()["content-type"]).toBe("application/pdf");
  expect((await pdf.body()).subarray(0, 5).toString()).toBe("%PDF-");

  // La solicitud aparece aceptada con su fecha de inscripción y su convocatoria.
  await page.getByRole("button", { name: "Cerrar sesión" }).click();
  await expect(page).toHaveURL("/");
  await page.goto("/aspirantes/estatus");
  await page.getByLabel("CURP").fill(ASPIRANT.curp);
  await page.getByLabel("Folio").fill(folio);
  await page.getByRole("button", { name: "Consultar" }).click();
  await expect(page.getByTestId("status")).toHaveText("Aceptado");
  await expect(page.getByText("Fecha de inscripción")).toBeVisible();
  await expect(page.getByText("2026-2")).toBeVisible();
});
