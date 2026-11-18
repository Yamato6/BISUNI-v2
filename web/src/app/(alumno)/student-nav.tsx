"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";

const LINKS = [
  { href: "/dashboard", label: "Inicio" },
  { href: "/calificaciones", label: "Calificaciones" },
  { href: "/kardex", label: "Kárdex" },
  { href: "/horario", label: "Horario" },
  { href: "/asistencia", label: "Asistencia" },
  { href: "/profesores", label: "Mis profesores" },
  { href: "/documentos", label: "Documentos" },
  { href: "/perfil", label: "Mi perfil" },
] as const;

export function StudentNav() {
  const pathname = usePathname();
  return (
    <nav aria-label="Portal del alumno" className="flex gap-1 overflow-x-auto md:flex-col">
      {LINKS.map((l) => {
        const active = pathname === l.href;
        return (
          <Link
            key={l.href}
            href={l.href}
            aria-current={active ? "page" : undefined}
            className={`whitespace-nowrap rounded-lg px-3 py-2.5 font-medium transition ${
              active ? "bg-white text-brand-deep shadow-sm" : "text-white/90 hover:bg-white/15"
            }`}
          >
            {l.label}
          </Link>
        );
      })}
    </nav>
  );
}
