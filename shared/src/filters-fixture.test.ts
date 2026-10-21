import { expect, test } from "bun:test";
import {
  articleTopicGroups,
  DEFAULT_SETTINGS,
  type FilterEntry,
  groupOrder,
  passes,
  type TopicCount,
  topicGroups,
} from "./filter.ts";
import { compareNames } from "./names.ts";
import {
  articlePlace,
  type CountedSection,
  feedSections,
  type SectionChip,
  type SectionSource,
  sectionCounts,
  sectionOff,
  toggledSection,
} from "./sections.ts";

/** `fixtures/filters.json`: sections, topic groups, hiding and toggling, which every client's tests read. */
interface FiltersFixture {
  names: { input: string[]; output: string[] };
  feeds: {
    name: string;
    input: FilterEntry[];
    output: {
      sections: SectionChip[][];
      counts: CountedSection[];
      groups: TopicCount[][];
      articleTopics: string[][][];
    };
  }[];
  places: { input: SectionSource; output: string }[];
  hides: {
    input: { offTopics: string[]; entry: FilterEntry };
    output: boolean;
  }[];
  toggles: {
    input: { offTopics: string[]; place: string };
    output: { off: boolean; offTopics: string[] };
  }[];
}

const fixture: FiltersFixture = await Bun.file(
  new URL("../fixtures/filters.json", import.meta.url),
).json();

test.each(fixture.feeds.map((item) => [item.name, item] as const))(
  "sections and topic groups of %s match the fixture",
  (_name, item) => {
    const sources = item.input.map((entry) => ({
      url: entry.url ?? "",
      sourceUrl: entry.sourceUrl,
    }));
    const sections = feedSections(sources);
    const groups = topicGroups(item.input);
    const order = groupOrder(groups);
    expect(sections).toEqual(item.output.sections);
    expect(sectionCounts(sections)).toEqual(item.output.counts);
    expect(groups).toEqual(item.output.groups);
    expect(
      item.input.map((entry) => articleTopicGroups(entry.categories, order)),
    ).toEqual(item.output.articleTopics);
  },
);

test("names sort as the fixture says", () => {
  expect([...fixture.names.input].sort(compareNames)).toEqual(
    fixture.names.output,
  );
});

test("article places match the fixture", () => {
  for (const item of fixture.places) {
    expect(articlePlace(item.input)).toBe(item.output);
  }
});

test("turned-off sections and topics hide as the fixture says", () => {
  for (const { input, output } of fixture.hides) {
    const settings = { ...DEFAULT_SETTINGS, offTopics: input.offTopics };
    expect(!passes(settings, input.entry)).toBe(output);
  }
});

test("tapping a section changes the stored places as the fixture says", () => {
  for (const { input, output } of fixture.toggles) {
    expect(sectionOff(input.offTopics, input.place)).toBe(output.off);
    expect(toggledSection(input.offTopics, input.place)).toEqual(
      output.offTopics,
    );
  }
});
