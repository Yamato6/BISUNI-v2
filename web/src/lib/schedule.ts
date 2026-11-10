/** Armado del horario semanal (lunes a viernes por bloques), sin dependencias del servidor. */

export const WEEKDAYS = ["Mon", "Tue", "Wed", "Thu", "Fri"] as const;
export type Weekday = (typeof WEEKDAYS)[number];

export const WEEKDAY_LABEL: Record<Weekday, string> = {
  Mon: "Lunes",
  Tue: "Martes",
  Wed: "Miércoles",
  Thu: "Jueves",
  Fri: "Viernes",
};

export interface TimeSlot {
  idSlot: number;
  slotNumber: number;
  startTime: string; // "07:00:00"
  endTime: string;
}

export interface ScheduleClass {
  day: Weekday;
  idSlot: number;
  idSubject: number;
  subjectName: string;
  teacherName: string;
}

export interface ScheduleRow {
  slot: TimeSlot;
  /** Una celda por día, en el orden de WEEKDAYS; null = bloque libre. */
  cells: Array<ScheduleClass | null>;
}

export function buildScheduleGrid(slots: TimeSlot[], classes: ScheduleClass[]): ScheduleRow[] {
  const byKey = new Map(classes.map((c) => [`${c.day}:${c.idSlot}`, c]));
  return [...slots]
    .sort((a, b) => a.slotNumber - b.slotNumber)
    .map((slot) => ({
      slot,
      cells: WEEKDAYS.map((d) => byKey.get(`${d}:${slot.idSlot}`) ?? null),
    }));
}

/** "07:00:00" → "07:00". */
export function hhmm(time: string): string {
  return time.slice(0, 5);
}

/** Color estable por materia (mismo id → mismo color), con contraste suficiente para texto oscuro. */
const PALETTE = ["#d4f5e2", "#dce8f5", "#f5e6d3", "#ebdcf0", "#f5f0c8", "#d3f0ee", "#f5dcdc", "#e3e3f7"];
export function subjectColor(idSubject: number): string {
  return PALETTE[(((idSubject * 7) % PALETTE.length) + PALETTE.length) % PALETTE.length];
}
