import { SvelteSet } from "svelte/reactivity";

/** How far outside a scrolling box, as a share of its height, a row counts as near. */
export const NEAR_MARGIN = "200% 0px";

/** How far outside a scrolling box rows are drawn, past where they count as near, so they are there to fetch ahead. */
export const DRAW_MARGIN = "400% 0px";

/** Picture addresses some row has come near, so every row with one, drawn again or not, shows it at once. */
export const fetchedPictures = new SvelteSet<string>();

/**
 * Tells rows whether they are on screen or within two screens of it in a
 * scrolling box, so they can draw and fetch their pictures before they show.
 */
export class Nearby {
  private readonly watcher: IntersectionObserver;
  private readonly listeners = new Map<Element, (near: boolean) => void>();

  /** Watch rows inside `root`. */
  constructor(root: Element) {
    this.watcher = new IntersectionObserver(
      (changes) => {
        for (const change of changes) {
          this.listeners.get(change.target)?.(change.isIntersecting);
        }
      },
      { root, rootMargin: NEAR_MARGIN },
    );
  }

  /** Call `onchange` whenever `row` comes near or goes; returns a function that stops. */
  watch(row: Element, onchange: (near: boolean) => void): () => void {
    this.listeners.set(row, onchange);
    this.watcher.observe(row);
    return (): void => {
      this.watcher.unobserve(row);
      this.listeners.delete(row);
    };
  }

  /** Stop watching every row. */
  disconnect(): void {
    this.watcher.disconnect();
    this.listeners.clear();
  }
}
