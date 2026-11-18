import { getCurrentPeriod } from "@/lib/academic";
import { requireStudent } from "@/lib/dal";
import { pdfResponse, renderConstancia } from "@/lib/pdf/documents";
import { getOverview } from "@/lib/student";

/** Constancia de estudios en PDF (o de egreso, si el alumno ya egresó). */
export async function GET() {
  const { idStudent, academicStatus } = await requireStudent();
  if (academicStatus === "Leave") {
    return new Response("Estás en baja temporal: la constancia de estudios la expide control escolar.", { status: 409 });
  }
  const [me, period] = await Promise.all([getOverview(idStudent), getCurrentPeriod(idStudent)]);
  if (!me) return new Response("No encontrado", { status: 404 });
  const pdf = await renderConstancia(me, academicStatus === "Graduated" ? null : period);
  return pdfResponse(pdf, `constancia-${me.enrollmentNumber}.pdf`);
}
