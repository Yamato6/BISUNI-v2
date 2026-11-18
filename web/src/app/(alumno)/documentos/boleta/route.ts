import type { NextRequest } from "next/server";
import { getKardex } from "@/lib/academic";
import { requireStudent } from "@/lib/dal";
import { pdfResponse, renderBoleta } from "@/lib/pdf/documents";
import { getOverview } from "@/lib/student";

/** Boleta de un periodo en PDF: ?periodo=<id>; sin parámetro, el periodo más reciente del alumno. */
export async function GET(request: NextRequest) {
  const { idStudent } = await requireStudent();
  const [me, kardex] = await Promise.all([getOverview(idStudent), getKardex(idStudent)]);
  if (!me || !kardex || kardex.periods.length === 0) {
    return new Response("Todavía no tienes calificaciones para una boleta.", { status: 404 });
  }
  const requested = Number(request.nextUrl.searchParams.get("periodo"));
  const period = Number.isInteger(requested) && requested > 0
    ? kardex.periods.find((p) => p.idPeriod === requested)
    : kardex.periods.at(-1);
  if (!period) return new Response("No cursaste materias en ese periodo.", { status: 404 });
  const pdf = await renderBoleta(me, period);
  return pdfResponse(pdf, `boleta-${period.name}-${me.enrollmentNumber}.pdf`);
}
