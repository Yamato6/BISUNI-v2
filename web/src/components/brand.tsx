import Image from "next/image";
import Link from "next/link";

export function Brand({ subtitle, href = "/", inverted = false }: { subtitle: string; href?: string; inverted?: boolean }) {
  return (
    <Link href={href} className="flex items-center gap-3">
      <Image src="/bis.png" alt="" width={44} height={41} priority className="h-11 w-auto" />
      <span className="leading-tight">
        <span className={`block font-display text-2xl font-bold ${inverted ? "text-white" : "text-brand-deep"}`}>BISUNI</span>
        <span className={`block text-xs ${inverted ? "text-white/80" : "text-muted"}`}>{subtitle}</span>
      </span>
    </Link>
  );
}
