/** Window width in CSS pixels below which the app uses its phone layout, Tailwind's `md`. */
export const NARROW_WIDTH = 768;

/** Whether the window is narrow enough for the phone layout. */
export function isNarrow(): boolean {
  return globalThis.innerWidth < NARROW_WIDTH;
}
