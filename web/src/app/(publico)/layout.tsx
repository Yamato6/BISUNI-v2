import { PublicHeader } from "@/components/public-header";

export default function PublicLayout({ children }: LayoutProps<"/">) {
  return (
    <>
      <PublicHeader />
      <main className="flex-1">{children}</main>
      <footer className="border-t border-line bg-white">
        <div className="mx-auto max-w-6xl px-4 py-5 text-sm text-muted">
          BISUNI · Sistema de Control Escolar
        </div>
      </footer>
    </>
  );
}
