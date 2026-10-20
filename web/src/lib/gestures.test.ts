import { expect, test } from "bun:test";
import {
  ARTICLE_PULL_DISTANCE,
  ARTICLE_PULL_STRETCH,
  ARTICLE_SWIPE_DISTANCE,
  articleSwipe,
  closesByPull,
  isEdgeSwipe,
  pullProgress,
  pullStretch,
  pullTravel,
  refreshPullTravel,
  startsAtEdge,
  swipesPastEnd,
  touchAxis,
} from "./gestures.ts";

test("a swipe in from the left edge opens the feed list", () => {
  expect(isEdgeSwipe({ x: 4, y: 300 }, { x: 120, y: 320 })).toBe(true);
  expect(startsAtEdge({ x: 4, y: 0 })).toBe(true);
});

test("swipes that start inside, are short, go left or mostly down do not", () => {
  expect(isEdgeSwipe({ x: 80, y: 300 }, { x: 220, y: 300 })).toBe(false);
  expect(isEdgeSwipe({ x: 4, y: 300 }, { x: 40, y: 300 })).toBe(false);
  expect(isEdgeSwipe({ x: 20, y: 300 }, { x: 0, y: 300 })).toBe(false);
  expect(isEdgeSwipe({ x: 4, y: 300 }, { x: 90, y: 420 })).toBe(false);
});

test("a touch is sideways only once it has moved and mostly sideways", () => {
  expect(touchAxis(4, 2)).toBe("none");
  expect(touchAxis(-30, 5)).toBe("sideways");
  expect(touchAxis(20, 18)).toBe("vertical");
  expect(touchAxis(2, 40)).toBe("vertical");
});

test("a long swipe on an article steps to the next or previous one", () => {
  expect(articleSwipe(-ARTICLE_SWIPE_DISTANCE, 1, 3)).toBe("step");
  expect(articleSwipe(ARTICLE_SWIPE_DISTANCE, 1, 3)).toBe("step");
});

test("a long swipe past either end of the list closes the article", () => {
  expect(articleSwipe(-ARTICLE_PULL_DISTANCE, 2, 3)).toBe("close");
  expect(articleSwipe(ARTICLE_PULL_DISTANCE, 0, 3)).toBe("close");
  expect(articleSwipe(-200, 0, 1)).toBe("close");
});

test("only a swipe beyond the first or last listed article goes past the end", () => {
  expect(swipesPastEnd(10, 0, 3)).toBe(true);
  expect(swipesPastEnd(-10, 2, 3)).toBe(true);
  expect(swipesPastEnd(-10, 0, 1)).toBe(true);
  expect(swipesPastEnd(-10, 0, 3)).toBe(false);
  expect(swipesPastEnd(10, 2, 3)).toBe(false);
  expect(swipesPastEnd(0, 0, 3)).toBe(false);
  expect(swipesPastEnd(10, -1, 3)).toBe(false);
});

test("a short nudge, or an article no longer listed, stays", () => {
  expect(articleSwipe(-(ARTICLE_SWIPE_DISTANCE - 1), 2, 3)).toBe("stay");
  expect(articleSwipe(ARTICLE_SWIPE_DISTANCE - 1, 0, 3)).toBe("stay");
  expect(articleSwipe(ARTICLE_PULL_DISTANCE - 1, 0, 3)).toBe("stay");
  expect(articleSwipe(-200, -1, 3)).toBe("stay");
});

test("pulling an article far past the edge it started at closes it", () => {
  expect(closesByPull(ARTICLE_PULL_DISTANCE, true, false)).toBe(true);
  expect(closesByPull(-ARTICLE_PULL_DISTANCE, false, true)).toBe(true);
  expect(closesByPull(-200, true, true)).toBe(true);
});

test("a short pull, or one away from the edge it started at, does not", () => {
  expect(closesByPull(ARTICLE_PULL_DISTANCE - 1, true, false)).toBe(false);
  expect(closesByPull(-200, true, false)).toBe(false);
  expect(closesByPull(200, false, true)).toBe(false);
  expect(closesByPull(200, false, false)).toBe(false);
});

test("only a drag on past the edge the touch began at counts as pulling", () => {
  expect(pullTravel(50, true, false)).toBe(50);
  expect(pullTravel(-50, false, true)).toBe(-50);
  expect(pullTravel(-50, true, false)).toBe(0);
  expect(pullTravel(50, false, true)).toBe(0);
  expect(pullTravel(50, false, false)).toBe(0);
});

test("pulling the list to refresh does nothing while the feeds are checked", () => {
  expect(refreshPullTravel(50, true, false, false)).toBe(50);
  expect(refreshPullTravel(-50, false, true, false)).toBe(-50);
  expect(refreshPullTravel(50, true, false, true)).toBe(0);
  expect(refreshPullTravel(-50, false, true, true)).toBe(0);
});

// The same numbers as Android's PullTest.
test("a pulled article moves less and less as the finger goes further", () => {
  expect(pullStretch(0)).toBe(0);
  expect(pullStretch(10)).toBeCloseTo(9.694, 3);
  expect(pullStretch(96)).toBeCloseTo(72.19, 3);
  expect(pullStretch(-96)).toBeCloseTo(-72.19, 3);
  expect(pullStretch(200)).toBeCloseTo(114.159, 3);
  expect(pullStretch(400)).toBeCloseTo(146.866, 3);
  expect(pullStretch(1000)).toBeLessThan(ARTICLE_PULL_STRETCH);
});

test("a pull's progress reaches 1 at the closing distance and stays there", () => {
  expect(pullProgress(0)).toBe(0);
  expect(pullProgress(48)).toBe(0.5);
  expect(pullProgress(-48)).toBe(0.5);
  expect(pullProgress(ARTICLE_PULL_DISTANCE)).toBe(1);
  expect(pullProgress(400)).toBe(1);
});
