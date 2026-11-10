import type { DefaultSession } from "next-auth";

declare module "next-auth" {
  interface User {
    idStudent?: number;
  }

  interface Session {
    user: {
      role: "Student";
      idUser: number;
      idStudent: number;
    } & DefaultSession["user"];
  }
}

// En Auth.js v5 la interfaz JWT vive en @auth/core; next-auth/jwt la reexporta.
declare module "@auth/core/jwt" {
  interface JWT {
    role: "Student";
    idUser: number;
    idStudent: number;
  }
}
