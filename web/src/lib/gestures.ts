/** How close to the left edge a touch must start to open the feed list. */
export const EDGE_WIDTH = 24;
/** How far a swipe must travel to count. */
const SWIPE_DISTANCE = 60;
/** How many times more sideways than up or down a swipe must go. */
export const SIDEWAYS_RATIO = 1.5;

/** How far a touch moves before it counts as sideways or up and down. */
const AXIS_SLOP = 10;

/** Which way a touch is going. */
export type Axis = "none" | "sideways" | "vertical";

/**
 * Which way a touch that has moved `across` and `down` pixels is going: none
 * until it has moved far enough to tell, sideways only when clearly more
 * sideways than up or down.
 */
export function touchAxis(across: number, down: number): Axis {
  let axis: Axis = "none";
  if (Math.max(Math.abs(across), Math.abs(down)) >= AXIS_SLOP) {
    axis =
      Math.abs(across) > Math.abs(down) * SIDEWAYS_RATIO
        ? "sideways"
        : "vertical";
  }
  return axis;
}

/** A touch position. */
export interface Point {
  x: number;
  y: number;
}

/** Whether a touch starts at the screen's left edge, where it opens the feed list rather than swiping a card. */
export function startsAtEdge(start: Point): boolean {
  return start.x <= EDGE_WIDTH;
}

/** Whether a touch from `start` to `end` is a swipe in from the left edge. */
export function isEdgeSwipe(start: Point, end: Point): boolean {
  const across = end.x - start.x;
  return (
    startsAtEdge(start) &&
    across >= SWIPE_DISTANCE &&
    across > Math.abs(end.y - start.y) * SIDEWAYS_RATIO
  );
}

/** How far a sideways swipe on an open article must travel to step to the next or previous one. */
export const ARTICLE_SWIPE_DISTANCE = 90;

/** What a finished sideways swipe on an open article does. */
export type ArticleSwipe = "stay" | "step" | "close";

/**
 * Whether a sideways swipe of `across` pixels on the article at `index` of a
 * list of `count` rows goes on past the first or last article, where it pulls
 * the article closed rather than stepping; never for an article the list no
 * longer holds.
 */
export function swipesPastEnd(
  across: number,
  index: number,
  count: number,
): boolean {
  const target = index + (across < 0 ? 1 : -1);
  return across !== 0 && index !== -1 && (target < 0 || target >= count);
}

/**
 * What a sideways swipe of `across` pixels does to the article at `index` of
 * a list of `count` rows: too short or with the article no longer listed, it
 * stays; otherwise it steps to the next (leftward) or previous article, and
 * on past either end, as far as a pull past the top or end must go, it closes
 * the article, as stepping does with the keys.
 */
export function articleSwipe(
  across: number,
  index: number,
  count: number,
): ArticleSwipe {
  let result: ArticleSwipe = "stay";
  if (swipesPastEnd(across, index, count)) {
    result = Math.abs(across) >= ARTICLE_PULL_DISTANCE ? "close" : "stay";
  } else if (Math.abs(across) >= ARTICLE_SWIPE_DISTANCE && index !== -1) {
    result = "step";
  }
  return result;
}

/** How far an article must be pulled past its top or bottom to close it. */
export const ARTICLE_PULL_DISTANCE = 96;

/** How far a pulled article can move however far the finger goes, in pixels. */
export const ARTICLE_PULL_STRETCH = 160;

/**
 * How far a drag of `down` pixels on an article that was at its top (`atTop`)
 * or its end (`atEnd`) when the touch began has gone past that edge: down
 * from the top is positive, up from the end negative, and a drag the other
 * way, which scrolls the article, is zero.
 */
export function pullTravel(
  down: number,
  atTop: boolean,
  atEnd: boolean,
): number {
  let travel = 0;
  if (atTop && down > 0) {
    travel = down;
  } else if (atEnd && down < 0) {
    travel = down;
  }
  return travel;
}

/**
 * How far a drag of `down` pixels on the list that was at its top (`atTop`)
 * or its end (`atEnd`) when the touch began has pulled it past that edge to
 * refresh, as {@link pullTravel}; zero while the feeds are being checked
 * (`refreshing`), when pulling does nothing.
 */
export function refreshPullTravel(
  down: number,
  atTop: boolean,
  atEnd: boolean,
  refreshing: boolean,
): number {
  return refreshing ? 0 : pullTravel(down, atTop, atEnd);
}

/**
 * Whether a drag of `down` pixels on an article that was at its top
 * (`atTop`) or its end (`atEnd`) when the touch began pulls it far enough
 * past that edge to close it.
 */
export function closesByPull(
  down: number,
  atTop: boolean,
  atEnd: boolean,
): boolean {
  return Math.abs(pullTravel(down, atTop, atEnd)) >= ARTICLE_PULL_DISTANCE;
}

/**
 * How far a pulled article moves when the finger has gone `travel` pixels
 * past its edge: at first with the finger, then less and less, never quite
 * reaching {@link ARTICLE_PULL_STRETCH}. Keeps the sign of `travel`.
 */
export function pullStretch(travel: number): number {
  return (
    Math.sign(travel) *
    ARTICLE_PULL_STRETCH *
    (1 - Math.exp(-Math.abs(travel) / ARTICLE_PULL_STRETCH))
  );
}

/** How far along a pull of `travel` pixels is toward closing the article, from 0 to 1. */
export function pullProgress(travel: number): number {
  return Math.min(1, Math.abs(travel) / ARTICLE_PULL_DISTANCE);
}

/** How long a haptic tick lasts, in milliseconds. */
const TICK_MS = 10;

/**
 * Give a very short vibration where the browser can (Chrome on Android), as a
 * drag crosses the point where letting go acts; elsewhere nothing happens.
 */
export function tick(): void {
  if (typeof navigator !== "undefined" && "vibrate" in navigator) {
    navigator.vibrate(TICK_MS);
  }
}
