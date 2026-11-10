import type { InputHTMLAttributes, ReactNode } from "react";

/** Campo con etiqueta, ayuda y error accesibles (aria-describedby / aria-invalid). */
export function FormField({
  id,
  label,
  error,
  hint,
  children,
}: {
  id: string;
  label: string;
  error?: string;
  hint?: string;
  children: ReactNode;
}) {
  return (
    <div>
      <label htmlFor={id} className="label">{label}</label>
      {children}
      {hint && !error && <p id={`${id}-hint`} className="mt-1 text-xs text-muted">{hint}</p>}
      {error && <p id={`${id}-error`} className="mt-1 text-sm text-bad">{error}</p>}
    </div>
  );
}

/** Props de accesibilidad para el control de un FormField. */
export function fieldProps(id: string, error?: string, hint?: string): InputHTMLAttributes<HTMLInputElement> {
  return {
    id,
    name: id,
    "aria-invalid": error ? true : undefined,
    "aria-describedby": error ? `${id}-error` : hint ? `${id}-hint` : undefined,
    className: `field ${error ? "border-bad" : ""}`,
  };
}
