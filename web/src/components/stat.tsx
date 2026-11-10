export function Stat({ label, value, hint }: { label: string; value: string | number; hint?: string }) {
  return (
    <div className="card">
      <p className="text-sm font-medium text-muted">{label}</p>
      <p className="mt-1 font-display text-3xl font-bold text-ink">{value}</p>
      {hint && <p className="mt-1 text-xs text-muted">{hint}</p>}
    </div>
  );
}
