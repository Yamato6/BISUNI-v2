"use server";

import { statusQuerySchema } from "@/lib/aspirant-rules";
import { findApplication, type Application } from "@/lib/aspirants";

export interface StatusState {
  application?: Application;
  error?: string;
  values?: { curp: string; folio: string };
}

export async function statusAction(_prev: StatusState, formData: FormData): Promise<StatusState> {
  const values = { curp: String(formData.get("curp") ?? ""), folio: String(formData.get("folio") ?? "") };
  const parsed = statusQuerySchema.safeParse(values);
  if (!parsed.success) {
    return { error: parsed.error.issues[0]?.message ?? "Revisa tu CURP y tu folio.", values };
  }
  const application = await findApplication(parsed.data.curp, parsed.data.folio);
  if (!application) {
    // No se dice cuál de los dos datos falló, para no confirmar CURPs o folios ajenos.
    return { error: "No encontramos una solicitud con esa CURP y ese folio. Revisa ambos datos.", values };
  }
  return { application, values };
}
