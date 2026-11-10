import Link from "next/link";
import { Brand } from "./brand";

const LINKS = [
  { href: "/", label: "Inicio" },
  { href: "/aspirantes/registro", label: "Registro de aspirantes" },
  { href: "/aspirantes/estatus", label: "Consultar estatus" },
];

export function PublicHeader() {
  return (
    <header className="border-b border-line bg-white">
      <div className="mx-auto flex max-w-6xl flex-wrap items-center justify-between gap-4 px-4 py-3">
        <Brand subtitle="Control Escolar" />
        <nav aria-label="Principal" className="flex flex-wrap items-center gap-1 text-sm">
          {LINKS.map((l) => (
            <Link key={l.href} href={l.href} className="rounded-md px-3 py-2 font-medium text-ink hover:bg-brand-soft hover:text-brand-deep">
              {l.label}
            </Link>
          ))}
          <Link href="/login" className="btn-primary ml-1 py-2 text-sm">
            Portal de alumnos
          </Link>
        </nav>
      </div>
    </header>
  );
}
