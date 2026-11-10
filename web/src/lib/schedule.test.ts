import { describe, expect, it } from "vitest";
import { buildScheduleGrid, hhmm, subjectColor } from "./schedule";

const slots = [
  { idSlot: 2, slotNumber: 2, startTime: "08:00:00", endTime: "09:00:00" },
  { idSlot: 1, slotNumber: 1, startTime: "07:00:00", endTime: "08:00:00" },
];

describe("schedule", () => {
  it("ordena por bloque y coloca cada clase en su día", () => {
    const grid = buildScheduleGrid(slots, [
      { day: "Wed", idSlot: 1, idSubject: 3, subjectName: "Redes", teacherName: "Ana" },
    ]);
    expect(grid.map((r) => r.slot.slotNumber)).toEqual([1, 2]);
    expect(grid[0].cells[2]?.subjectName).toBe("Redes");
    expect(grid[0].cells.filter(Boolean)).toHaveLength(1);
    expect(grid[1].cells.every((c) => c === null)).toBe(true);
  });

  it("formatea horas y asigna colores estables", () => {
    expect(hhmm("07:00:00")).toBe("07:00");
    expect(subjectColor(5)).toBe(subjectColor(5));
    expect(subjectColor(1)).not.toBe(subjectColor(2));
  });
});
