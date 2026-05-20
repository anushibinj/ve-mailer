/**
 * Formats a 24-hour integer (0–23) as a human-readable 12-hour AM/PM label.
 *
 * Examples:
 *   0  → "12 AM"
 *   1  → "1 AM"
 *   9  → "9 AM"
 *   12 → "12 PM"
 *   13 → "1 PM"
 *   23 → "11 PM"
 *
 * The underlying numeric value is never changed — only the display label.
 */
export function formatHourLabel(hour: number): string {
  if (hour === 0) return '12 AM';
  if (hour < 12) return `${hour} AM`;
  if (hour === 12) return '12 PM';
  return `${hour - 12} PM`;
}
