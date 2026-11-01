import { expect, test } from "bun:test";
import { infoPageAt, isInfoPage } from "./info-page.ts";

const origin = "http://localhost:5173";

test("links to the app's pages, by path or on the site, open in the app", () => {
  expect(infoPageAt("/privacy", origin)).toBe("privacy");
  expect(infoPageAt(`${origin}/contact`, origin)).toBe("contact");
  expect(infoPageAt("https://reader.hafa.cc/terms", origin)).toBe("terms");
  expect(infoPageAt("https://reader.hafa.cc/terms/", origin)).toBe("terms");
});

test("other links do not", () => {
  expect(infoPageAt("/delete", origin)).toBeUndefined();
  expect(infoPageAt("mailto:support@reader.hafa.cc", origin)).toBeUndefined();
  expect(infoPageAt("https://example.com/privacy", origin)).toBeUndefined();
});

test("only those pages count", () => {
  expect(isInfoPage("terms")).toBe(true);
  expect(isInfoPage("settings")).toBe(false);
});
