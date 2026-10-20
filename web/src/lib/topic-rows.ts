/** The most rows of topics shown before the topics scroll sideways. */
export const TOPIC_ROWS = 3;

/**
 * The rows that items of these widths fill when laid left to right and
 * wrapped at the available width, with a gap between neighbors.
 */
export function wrappedRows(
  widths: number[],
  available: number,
  gap: number,
): number {
  let rows = 0;
  let used = 0;
  for (const width of widths) {
    if (rows === 0 || used + gap + width > available) {
      rows += 1;
      used = width;
    } else {
      used += gap + width;
    }
  }
  return rows;
}

/** Where one item goes among packed rows: its row, counted from zero, and its left edge. */
export interface RowPosition {
  /** The row, counted from zero. */
  row: number;
  /** The left edge. */
  left: number;
}

/**
 * Where items of these widths go in `rows` rows that grow sideways without
 * limit: each in turn goes at the end of the row that is shortest so far (the
 * upper one on a tie), with a gap after the item before it.
 */
export function packedRows(
  widths: number[],
  rows: number,
  gap: number,
): RowPosition[] {
  // where each row ends, counting a gap before its first item
  const ends: number[] = Array.from({ length: rows }, () => -gap);
  const positions: RowPosition[] = [];
  for (const width of widths) {
    const row = ends.indexOf(Math.min(...ends));
    const left = (ends[row] ?? 0) + gap;
    ends[row] = left + width;
    positions.push({ row, left });
  }
  return positions;
}

/** What each measured element reports its width to, by element. */
const reporters = new Map<Element, (width: number) => void>();
let sharedObserver: ResizeObserver | undefined;

function widthOf(node: Element): number {
  return node.getBoundingClientRect().width;
}

/** The one observer every measured element shares, made when first needed. */
function observer(): ResizeObserver {
  sharedObserver ??= new ResizeObserver((records) => {
    for (const record of records) {
      reporters.get(record.target)?.(
        record.borderBoxSize[0]?.inlineSize ?? widthOf(record.target),
      );
    }
  });
  return sharedObserver;
}

/**
 * An attachment that reports an element's width with its fractions of a
 * pixel, at once and whenever it changes, so a fit worked out from widths
 * matches how the browser wraps. Every element is watched by one shared
 * observer.
 */
export function measureWidth(
  report: (width: number) => void,
): (node: HTMLElement) => () => void {
  return (node) => {
    reporters.set(node, report);
    report(widthOf(node));
    observer().observe(node);
    return () => {
      reporters.delete(node);
      sharedObserver?.unobserve(node);
    };
  };
}
