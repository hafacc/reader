/** How far outside a scrolling box, as a share of its height, a row counts as near. */
export const NEAR_MARGIN = "200% 0px";

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
