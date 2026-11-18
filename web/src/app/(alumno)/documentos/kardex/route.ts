import { getKardex } from "@/lib/academic";
import { requireStudent } from "@/lib/dal";
import { pdfResponse, renderKardex } from "@/lib/pdf/documents";
import { getOverview } from "@/lib/student";

/** Kárdex completo en PDF. */
export async function GET() {
  const { idStudent } = await requireStudent();
  const [me, kardex] = await Promise.all([getOverview(idStudent), getKardex(idStudent)]);
  if (!me || !kardex) return new Response("No encontrado", { status: 404 });
  const pdf = await renderKardex(me, kardex);
  return pdfResponse(pdf, `kardex-${me.enrollmentNumber}.pdf`);
}
