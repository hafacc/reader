import { expect, test } from "bun:test";
import { candidateSize, isLarger, srcsetCandidates } from "./srcset.ts";

test("candidates are read as the spec reads them", () => {
  expect(srcsetCandidates(" a.png, b.png 2x,c.png 3x , d.png,")).toEqual([
    { address: "a.png", descriptors: "" },
    { address: "b.png", descriptors: "2x" },
    { address: "c.png", descriptors: "3x" },
    { address: "d.png", descriptors: "" },
  ]);
});

test("a candidate's size comes from its width, or its density times the declared width", () => {
  expect(candidateSize("640w", 0)).toEqual({ width: 640, density: 1 });
  expect(candidateSize("2x", 300)).toEqual({ width: 600, density: 2 });
  expect(candidateSize("1.5x", 0)).toEqual({ width: 0, density: 1.5 });
  expect(candidateSize("", 200)).toEqual({ width: 200, density: 1 });
  expect(candidateSize("wide", 0)).toEqual({ width: 0, density: 1 });
});

test("a known width beats an unknown one, and density breaks ties", () => {
  expect(isLarger({ width: 640, density: 1 }, { width: 0, density: 3 })).toBe(
    true,
  );
  expect(isLarger({ width: 0, density: 2 }, { width: 0, density: 1 })).toBe(
    true,
  );
  expect(isLarger({ width: 300, density: 1 }, { width: 300, density: 1 })).toBe(
    false,
  );
});
