import path from "node:path";
import { loadEnvConfig } from "@next/env";
import type { NextConfig } from "next";

// El .env vive en la raíz del monorepo y lo comparten desktop/ y web/.
// forceReload: Next ya cargó (y cacheó) los .env de web/; sin forzar, este llamado no lee nada.
loadEnvConfig(path.resolve(__dirname, ".."), process.env.NODE_ENV !== "production", console, true);

const nextConfig: NextConfig = {
  // El driver de Postgres solo corre en el servidor.
  serverExternalPackages: ["postgres"],
  turbopack: {
    rules: {
      // Tailwind CSS v4 se procesa con su loader de Turbopack (lo configura create-next-app).
      "*.css": {
        loaders: ["@tailwindcss/turbopack"],
        as: "*.css",
      },
    },
  },
};

export default nextConfig;
