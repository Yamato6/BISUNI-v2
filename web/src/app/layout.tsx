import type { Metadata } from "next";
import { Poppins } from "next/font/google";
import localFont from "next/font/local";
import "./globals.css";

const poppins = Poppins({
  variable: "--font-poppins",
  subsets: ["latin"],
  weight: ["400", "500", "600", "700"],
});

// Fuente de marca de la v1 (Main_font/NationalPark).
const nationalPark = localFont({
  variable: "--font-national-park",
  src: "./fonts/NationalPark-VariableFont_wght.ttf",
  weight: "200 800",
});

export const metadata: Metadata = {
  title: { default: "BISUNI — Control Escolar", template: "%s · BISUNI" },
  description: "Portal de alumnos y aspirantes de BISUNI.",
  icons: { icon: "/bis.png" },
};

export default function RootLayout({ children }: LayoutProps<"/">) {
  return (
    <html lang="es" className={`${poppins.variable} ${nationalPark.variable} h-full antialiased`}>
      <body className="flex min-h-full flex-col font-sans">{children}</body>
    </html>
  );
}
