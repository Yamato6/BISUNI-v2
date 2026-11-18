import "server-only";
import { readFileSync } from "node:fs";
import path from "node:path";
import { Document, Font, Image, Page, StyleSheet, Text, View, renderToBuffer } from "@react-pdf/renderer";
import { RESULT_LABEL, type KardexPeriod } from "@/lib/academic-rules";
import { formatGrade } from "@/lib/grades";
import type { Kardex } from "@/lib/academic";
import { ACADEMIC_STATUS_LABEL, type StudentOverview } from "@/lib/student";

/**
 * Documentos del alumno en PDF (se generan en el servidor con @react-pdf/renderer).
 * Usan la fuente Helvetica integrada, que cubre acentos y ñ.
 */

// Sin separación silábica automática: partía palabras en español con guion ("semes-tre").
Font.registerHyphenationCallback((word) => [word]);

const GREEN = "#1e8449";
const MUTED = "#6b7c86";
const LINE = "#d5dde3";

const styles = StyleSheet.create({
  page: { paddingTop: 36, paddingBottom: 54, paddingHorizontal: 44, fontSize: 10, fontFamily: "Helvetica", color: "#2c3e50" },
  header: { flexDirection: "row", alignItems: "center", borderBottomWidth: 2, borderBottomColor: GREEN, paddingBottom: 10, marginBottom: 18 },
  logo: { width: 46, height: 43, marginRight: 12 },
  school: { fontSize: 18, fontFamily: "Helvetica-Bold", color: GREEN },
  subtitle: { fontSize: 9, color: MUTED },
  title: { fontSize: 15, fontFamily: "Helvetica-Bold", marginBottom: 10, textAlign: "center" },
  paragraph: { fontSize: 11, lineHeight: 1.6, marginBottom: 10, textAlign: "justify" },
  bold: { fontFamily: "Helvetica-Bold" },
  data: { flexDirection: "row", flexWrap: "wrap", marginBottom: 12 },
  dataItem: { width: "50%", marginBottom: 6 },
  label: { fontSize: 8, color: MUTED, textTransform: "uppercase" },
  value: { fontSize: 10, fontFamily: "Helvetica-Bold" },
  periodTitle: { fontSize: 11, fontFamily: "Helvetica-Bold", color: GREEN, marginTop: 10, marginBottom: 4 },
  table: { borderTopWidth: 1, borderTopColor: LINE },
  row: { flexDirection: "row", borderBottomWidth: 1, borderBottomColor: LINE, paddingVertical: 4 },
  th: { fontFamily: "Helvetica-Bold", fontSize: 8, color: MUTED },
  colSubject: { flex: 1, paddingRight: 6 },
  colSmall: { width: 44, textAlign: "center" },
  colResult: { width: 64 },
  summary: { fontSize: 9, color: MUTED, marginTop: 4, textAlign: "right" },
  signature: { marginTop: 60, alignItems: "center" },
  signatureLine: { width: 200, borderTopWidth: 1, borderTopColor: "#2c3e50", marginBottom: 4 },
  footer: { position: "absolute", bottom: 26, left: 44, right: 44, fontSize: 8, color: MUTED, flexDirection: "row", justifyContent: "space-between" },
});

let logoCache: Buffer | null = null;
function logo(): Buffer {
  logoCache ??= readFileSync(path.join(process.cwd(), "public", "bis.png"));
  return logoCache;
}

const LONG_DATE = new Intl.DateTimeFormat("es-MX", { day: "numeric", month: "long", year: "numeric", timeZone: "America/Mexico_City" });

function Header({ subtitle }: { subtitle: string }) {
  return (
    <View style={styles.header}>
      {/* eslint-disable-next-line jsx-a11y/alt-text -- Image de react-pdf, no de HTML */}
      <Image style={styles.logo} src={{ data: logo(), format: "png" }} />
      <View>
        <Text style={styles.school}>BISUNI</Text>
        <Text style={styles.subtitle}>Universidad · Control Escolar · {subtitle}</Text>
      </View>
    </View>
  );
}

function Footer({ issued, folio }: { issued: Date; folio: string }) {
  return (
    <View style={styles.footer} fixed>
      <Text>Emitido el {LONG_DATE.format(issued)} · Folio {folio}</Text>
      <Text render={({ pageNumber, totalPages }) => `Página ${pageNumber} de ${totalPages}`} />
    </View>
  );
}

function Data({ items }: { items: Array<[string, string]> }) {
  return (
    <View style={styles.data}>
      {items.map(([k, v]) => (
        <View key={k} style={styles.dataItem}>
          <Text style={styles.label}>{k}</Text>
          <Text style={styles.value}>{v}</Text>
        </View>
      ))}
    </View>
  );
}

/** Folio legible del documento: tipo + matrícula + fecha (AAAAMMDD). */
export function documentFolio(kind: "CE" | "KX" | "BO", enrollmentNumber: string, issued: Date): string {
  const d = issued.toISOString().slice(0, 10).replaceAll("-", "");
  return `${kind}-${enrollmentNumber}-${d}`;
}

function studentItems(me: StudentOverview): Array<[string, string]> {
  return [
    ["Alumno", `${me.firstName} ${me.lastName}`],
    ["Matrícula", me.enrollmentNumber],
    ["Carrera", me.careerName],
    ["Situación", ACADEMIC_STATUS_LABEL[me.academicStatus]],
  ];
}

// ---------------------------------------------------------------------------

export interface ConstanciaPeriod {
  name: string;
  startDate: string;
  endDate: string;
}

function ConstanciaDoc({ me, period, issued }: { me: StudentOverview; period: ConstanciaPeriod | null; issued: Date }) {
  const folio = documentFolio("CE", me.enrollmentNumber, issued);
  const name = `${me.firstName} ${me.lastName}`;
  const fmt = (d: string) => LONG_DATE.format(new Date(`${d}T12:00:00Z`));
  return (
    <Document title={`Constancia de estudios - ${me.enrollmentNumber}`} author="BISUNI">
      <Page size="LETTER" style={styles.page}>
        <Header subtitle="Constancia de estudios" />
        <Text style={styles.title}>CONSTANCIA DE ESTUDIOS</Text>
        <Text style={styles.paragraph}>A quien corresponda:</Text>
        {me.academicStatus === "Graduated" ? (
          <Text style={styles.paragraph}>
            Se hace constar que <Text style={styles.bold}>{name}</Text>, con matrícula{" "}
            <Text style={styles.bold}>{me.enrollmentNumber}</Text>, cursó la carrera de{" "}
            <Text style={styles.bold}>{me.careerName}</Text> en esta institución y es egresado(a) de la misma.
          </Text>
        ) : (
          <Text style={styles.paragraph}>
            Se hace constar que <Text style={styles.bold}>{name}</Text>, con matrícula{" "}
            <Text style={styles.bold}>{me.enrollmentNumber}</Text>, es alumno(a) inscrito(a) en el{" "}
            <Text style={styles.bold}>{me.semester}° semestre</Text> de la carrera de{" "}
            <Text style={styles.bold}>{me.careerName}</Text>
            {me.groupName ? <Text>, en el grupo <Text style={styles.bold}>{me.groupName}</Text></Text> : null}
            {period ? (
              <Text>
                , durante el periodo escolar <Text style={styles.bold}>{period.name}</Text>, que comprende del{" "}
                {fmt(period.startDate)} al {fmt(period.endDate)}
              </Text>
            ) : null}
            .
          </Text>
        )}
        <Text style={styles.paragraph}>
          Se extiende la presente a petición del interesado(a) para los fines que a él (ella) convengan.
        </Text>
        <Data items={[...studentItems(me), ["Promedio general", formatGrade(me.gpa)]]} />
        <View style={styles.signature}>
          <View style={styles.signatureLine} />
          <Text>Control Escolar</Text>
          <Text style={styles.subtitle}>Documento generado desde el portal del alumno</Text>
        </View>
        <Footer issued={issued} folio={folio} />
      </Page>
    </Document>
  );
}

function PeriodTable({ period, withPartials }: { period: KardexPeriod; withPartials: boolean }) {
  return (
    <View wrap={false}>
      <Text style={styles.periodTitle}>
        Periodo {period.name} · {period.groups.join(", ")}
        {period.status === "Active" ? " (en curso)" : ""}
      </Text>
      <View style={styles.table}>
        <View style={styles.row}>
          <Text style={[styles.th, styles.colSubject]}>MATERIA</Text>
          <Text style={[styles.th, styles.colSmall]}>CRÉD.</Text>
          {withPartials && <Text style={[styles.th, styles.colSmall]}>P1</Text>}
          {withPartials && <Text style={[styles.th, styles.colSmall]}>P2</Text>}
          {withPartials && <Text style={[styles.th, styles.colSmall]}>P3</Text>}
          <Text style={[styles.th, styles.colSmall]}>FINAL</Text>
          <Text style={[styles.th, styles.colResult]}>RESULTADO</Text>
        </View>
        {period.entries.map((e) => (
          <View key={`${e.groupName}-${e.idSubject}`} style={styles.row}>
            <Text style={styles.colSubject}>{e.subjectName}</Text>
            <Text style={styles.colSmall}>{e.credits}</Text>
            {withPartials && <Text style={styles.colSmall}>{formatGrade(e.parcial1)}</Text>}
            {withPartials && <Text style={styles.colSmall}>{formatGrade(e.parcial2)}</Text>}
            {withPartials && <Text style={styles.colSmall}>{formatGrade(e.parcial3)}</Text>}
            <Text style={[styles.colSmall, styles.bold]}>{formatGrade(e.finalGrade)}</Text>
            <Text style={styles.colResult}>{RESULT_LABEL[e.result]}</Text>
          </View>
        ))}
      </View>
      <Text style={styles.summary}>
        Promedio del periodo: {formatGrade(period.average)} · Créditos aprobados: {period.creditsPassed}
      </Text>
    </View>
  );
}

function KardexDoc({ me, kardex, issued }: { me: StudentOverview; kardex: Kardex; issued: Date }) {
  return (
    <Document title={`Kárdex - ${me.enrollmentNumber}`} author="BISUNI">
      <Page size="LETTER" style={styles.page}>
        <Header subtitle="Kárdex" />
        <Text style={styles.title}>KÁRDEX</Text>
        <Data
          items={[
            ...studentItems(me),
            ["Promedio general", formatGrade(kardex.gpa)],
            ["Créditos aprobados", `${kardex.credits.approved} de ${kardex.credits.total} (${kardex.credits.percent} %)`],
          ]}
        />
        {kardex.periods.length === 0 && <Text style={styles.paragraph}>Sin materias registradas.</Text>}
        {kardex.periods.map((p) => (
          <PeriodTable key={p.idPeriod} period={p} withPartials={false} />
        ))}
        {kardex.credits.owed.length > 0 && (
          <Text style={[styles.paragraph, { marginTop: 12 }]}>Materias adeudadas: {kardex.credits.owed.join(", ")}.</Text>
        )}
        <Footer issued={issued} folio={documentFolio("KX", me.enrollmentNumber, issued)} />
      </Page>
    </Document>
  );
}

function BoletaDoc({ me, period, issued }: { me: StudentOverview; period: KardexPeriod; issued: Date }) {
  return (
    <Document title={`Boleta ${period.name} - ${me.enrollmentNumber}`} author="BISUNI">
      <Page size="LETTER" style={styles.page}>
        <Header subtitle="Boleta de calificaciones" />
        <Text style={styles.title}>BOLETA DE CALIFICACIONES · {period.name}</Text>
        <Data items={[...studentItems(me), ["Grupo", period.groups.join(", ")], ["Promedio del periodo", formatGrade(period.average)]]} />
        <PeriodTable period={period} withPartials />
        {period.status !== "Closed" && (
          <Text style={[styles.subtitle, { marginTop: 10 }]}>
            Periodo en curso: las calificaciones son definitivas cuando el profesor cierra el acta.
          </Text>
        )}
        <Footer issued={issued} folio={documentFolio("BO", me.enrollmentNumber, issued)} />
      </Page>
    </Document>
  );
}

// ---------------------------------------------------------------------------

export function renderConstancia(me: StudentOverview, period: ConstanciaPeriod | null, issued = new Date()) {
  return renderToBuffer(<ConstanciaDoc me={me} period={period} issued={issued} />);
}

export function renderKardex(me: StudentOverview, kardex: Kardex, issued = new Date()) {
  return renderToBuffer(<KardexDoc me={me} kardex={kardex} issued={issued} />);
}

export function renderBoleta(me: StudentOverview, period: KardexPeriod, issued = new Date()) {
  return renderToBuffer(<BoletaDoc me={me} period={period} issued={issued} />);
}

/** Respuesta HTTP de descarga para un PDF. */
export function pdfResponse(pdf: Buffer, filename: string): Response {
  return new Response(new Uint8Array(pdf), {
    headers: {
      "Content-Type": "application/pdf",
      "Content-Disposition": `attachment; filename="${filename}"`,
      "Cache-Control": "private, no-store",
    },
  });
}
