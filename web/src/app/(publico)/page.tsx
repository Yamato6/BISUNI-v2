import Link from "next/link";

const PILLARS = [
  {
    title: "Nuestra misión",
    text: "Formar profesionales competentes y éticos, comprometidos con el desarrollo de la sociedad mediante una educación integral de calidad.",
  },
  {
    title: "Nuestra visión",
    text: "Ser una institución educativa líder, reconocida por su excelencia académica y su contribución al desarrollo tecnológico y social.",
  },
  {
    title: "Nuestros valores",
    text: "Integridad, responsabilidad, excelencia, innovación y compromiso social son los pilares de nuestra comunidad universitaria.",
  },
];

export default function HomePage() {
  return (
    <>
      <section className="bg-gradient-to-br from-brand-dark to-brand-deep text-white">
        <div className="mx-auto max-w-6xl px-4 py-20">
          <h1 className="text-4xl font-bold sm:text-5xl">Bienvenido a BISUNI</h1>
          <p className="mt-3 max-w-2xl text-lg text-white/90">Tu futuro comienza aquí. Sistema de Control Escolar.</p>
          <div className="mt-8 flex flex-wrap gap-3">
            <Link href="/aspirantes/registro" className="inline-flex items-center rounded-lg bg-white px-5 py-3 font-semibold text-brand-deep hover:bg-brand-soft">
              Soy aspirante
            </Link>
            <Link href="/login" className="inline-flex items-center rounded-lg border-2 border-white px-5 py-3 font-semibold text-white hover:bg-white/10">
              Soy alumno
            </Link>
          </div>
        </div>
      </section>

      <section className="mx-auto grid max-w-6xl gap-5 px-4 py-14 md:grid-cols-3">
        {PILLARS.map((p) => (
          <article key={p.title} className="card">
            <h2 className="text-xl font-bold text-brand-deep">{p.title}</h2>
            <p className="mt-2 text-muted">{p.text}</p>
          </article>
        ))}
      </section>

      <section className="mx-auto max-w-6xl px-4 pb-16">
        <div className="card flex flex-col items-start justify-between gap-4 md:flex-row md:items-center">
          <div>
            <h2 className="text-2xl font-bold">¿Listo para comenzar tu camino universitario?</h2>
            <p className="text-muted">Regístrate como aspirante o, si ya eres alumno, entra a tu portal.</p>
          </div>
          <div className="flex flex-wrap gap-3">
            <Link href="/aspirantes/registro" className="btn-primary">Registro de aspirantes</Link>
            <Link href="/aspirantes/estatus" className="btn-secondary">Consultar estatus</Link>
          </div>
        </div>
      </section>
    </>
  );
}
