import { compareNames } from "./names.ts";
import {
  articlePlace,
  offPlaces,
  placeHidden,
  SECTION_PREFIX,
} from "./sections.ts";

/** The details of an article that filters look at. */
export interface FilterEntry {
  /** The article's title. */
  title: string;
  /** The article's summary, as plain text. */
  summary: string;
  /** Sections, topics and tags the feed lists for the article. */
  categories: string[];
  /** The scheme of each of `categories` at the same index; empty or missing for none. */
  categorySchemes?: string[];
  /** Link to the original; sections come from it. Missing counts as none. */
  url?: string;
  /** Address of the story's source, which sections come from instead; empty or missing for none. */
  sourceUrl?: string;
}

/** A topic with how many articles carry it. */
export interface TopicCount {
  /** The topic as the last article carrying it spelled it. */
  topic: string;
  /** Articles that carry it. */
  count: number;
}

/** How one feed, group or the main list is shown. */
export interface ViewSettings {
  /** Order of the list by date. */
  sort: "newest" | "oldest";
  /** Words or phrases that hide an article when its title or summary has one. */
  hideWords: string[];
  /** When not empty, only articles whose title or summary has one of these stay. */
  onlyWords: string[];
  /**
   * Topics turned off; an article whose topics are all off is hidden. Entries
   * starting with `SECTION_PREFIX` hold turned-off sections' places instead,
   * which hide every article in them.
   */
  offTopics: string[];
}

/** Settings before anything is changed. */
export const DEFAULT_SETTINGS: ViewSettings = {
  sort: "newest",
  hideWords: [],
  onlyWords: [],
  offTopics: [],
};

// Same word and whitespace sets as subtube's pattern language, so a phrase
// means the same on every platform and in every engine.
const WORD = "A-Za-z0-9_";
const SPACE =
  "\\u0009-\\u000D\\u0020\\u0085\\u00A0\\u1680\\u2000-\\u200A\\u2028\\u2029\\u202F\\u205F\\u3000";
const WORD_CHARACTER = new RegExp(`^[${WORD}]$`, "u");
const SPACE_CHARACTER = new RegExp(`^[${SPACE}]$`, "u");
const SPECIAL = new Set("\\^$.|?*+()[]{}");

type Matcher = (text: string) => boolean;

/** Matchers already built, so each list of words is compiled once. */
const matchers = new Map<string, Matcher>();
// Lists kept before the cache starts over, so edited lists do not pile up.
const MATCHER_LIMIT = 64;

function isSpace(character: string | undefined): boolean {
  return character !== undefined && SPACE_CHARACTER.test(character);
}

function isWordCharacter(character: string | undefined): boolean {
  return character !== undefined && WORD_CHARACTER.test(character);
}

function trimSpace(characters: string[]): string[] {
  let start = 0;
  let end = characters.length;
  while (start < end && isSpace(characters[start])) {
    start += 1;
  }
  while (end > start && isSpace(characters[end - 1])) {
    end -= 1;
  }
  return characters.slice(start, end);
}

function escapeBody(characters: string[]): string {
  let body = "";
  for (const [index, character] of characters.entries()) {
    if (isSpace(character)) {
      body += isSpace(characters[index - 1]) ? "" : `[${SPACE}]+`;
    } else if (SPECIAL.has(character)) {
      body += `\\${character}`;
    } else {
      body += character;
    }
  }
  return body;
}

/**
 * One phrase as a pattern: runs of whitespace match any whitespace, and a
 * word boundary is required at an end only when that end is a word character.
 */
function phrasePattern(phrase: string): string {
  const trimmed = trimSpace(Array.from(phrase));
  let pattern = "";
  if (trimmed.length > 0) {
    const before = isWordCharacter(trimmed[0]) ? `(?<![${WORD}])` : "";
    const after = isWordCharacter(trimmed.at(-1)) ? `(?![${WORD}])` : "";
    pattern = `${before}${escapeBody(trimmed)}${after}`;
  }
  return pattern;
}

function never(): boolean {
  return false;
}

/** A function telling whether a text has any of the words; built once per list. */
function matcherFor(words: string[]): Matcher {
  const key = words.join("\n");
  let matcher = matchers.get(key);
  if (matcher === undefined) {
    const parts = new Set(words.map(phrasePattern));
    parts.delete("");
    matcher = never;
    if (parts.size > 0) {
      const pattern = new RegExp([...parts].join("|"), "iu");
      matcher = (text): boolean => pattern.test(text);
    }
    if (matchers.size >= MATCHER_LIMIT) {
      matchers.clear();
    }
    matchers.set(key, matcher);
  }
  return matcher;
}

/** Whether an article's title or summary has any of the words. */
export function hasWord(words: string[], entry: FilterEntry): boolean {
  return matchesEntry(matcherFor(words), entry);
}

function matchesEntry(matcher: Matcher, entry: FilterEntry): boolean {
  return matcher(entry.title) || matcher(entry.summary);
}

/**
 * The topics the articles carry, with how many articles carry each,
 * commonest first and then by name.
 */
export function topicCounts(entries: readonly FilterEntry[]): TopicCount[] {
  const counts = new Map<string, TopicCount>();
  for (const entry of entries) {
    const seen = new Set<string>();
    for (const topic of entry.categories) {
      const key = topic.trim().toLowerCase();
      if (!seen.has(key)) {
        seen.add(key);
        const known = counts.get(key);
        counts.set(key, { topic, count: (known?.count ?? 0) + 1 });
      }
    }
  }
  return [...counts.values()].sort((first, second) =>
    compareCounted(first.count, first.topic, second.count, second.topic),
  );
}

function compareCounted(
  firstCount: number,
  firstName: string,
  secondCount: number,
  secondName: string,
): number {
  return secondCount - firstCount || compareNames(firstName, secondName);
}

/**
 * The names with the highest counts first and then by name, the order every
 * list of topics or words is shown in.
 */
export function byCount(
  names: string[],
  count: (name: string) => number,
): string[] {
  return names
    .map((name) => ({ name, count: count(name) }))
    .sort((first, second) =>
      compareCounted(first.count, first.name, second.count, second.name),
    )
    .map(({ name }) => name);
}

/**
 * Each topic's place in the view's order, by its trimmed lower-case form,
 * from `topicCounts`; built once per view so each article looks its topics up.
 */
export function topicRanks(counts: readonly TopicCount[]): Map<string, number> {
  const ranks = new Map<string, number>();
  for (const [index, { topic }] of counts.entries()) {
    ranks.set(topic.trim().toLowerCase(), index);
  }
  return ranks;
}

/**
 * An article's topics, each once, in the view's order (`topicRanks`): most
 * articles in the view first, then by name; topics the view does not count go
 * last, by name.
 */
export function articleTopics(
  categories: string[],
  ranks: ReadonlyMap<string, number>,
): string[] {
  const seen = new Set<string>();
  const unique: { topic: string; rank: number }[] = [];
  for (const topic of categories) {
    const key = topic.trim().toLowerCase();
    if (!seen.has(key)) {
      seen.add(key);
      unique.push({ topic, rank: ranks.get(key) ?? Number.POSITIVE_INFINITY });
    }
  }
  return unique
    .sort(
      (first, second) =>
        first.rank - second.rank || compareNames(first.topic, second.topic),
    )
    .map(({ topic }) => topic);
}

/**
 * A test of whether an article stays after the settings' filters are
 * applied, built once for testing many articles.
 */
export function keeper(
  settings: ViewSettings,
): (entry: FilterEntry) => boolean {
  const off = new Set(
    settings.offTopics
      .filter((topic) => !topic.startsWith(SECTION_PREFIX))
      .map((topic) => topic.toLowerCase()),
  );
  const places = offPlaces(settings.offTopics);
  const hide = matcherFor(settings.hideWords);
  const only = matcherFor(settings.onlyWords);
  const onlySome = settings.onlyWords.length > 0;
  return (entry): boolean => {
    const topicsOff =
      off.size > 0 &&
      entry.categories.length > 0 &&
      entry.categories.every((topic) => off.has(topic.trim().toLowerCase()));
    const hidden =
      matchesEntry(hide, entry) ||
      (places.length > 0 &&
        placeHidden(
          places,
          articlePlace({ url: entry.url ?? "", sourceUrl: entry.sourceUrl }),
        ));
    const kept = !onlySome || matchesEntry(only, entry);
    return !(topicsOff || hidden) && kept;
  };
}

function topicKey(topic: string): string {
  return topic.trim().toLowerCase();
}

/** Each topic of an article with its scheme ("" for none), each pair once, blank topics left out. */
function schemedTopics(entry: FilterEntry): [string, string][] {
  const pairs = new Map<string, [string, string]>();
  for (const [index, topic] of entry.categories.entries()) {
    const key = topicKey(topic);
    const scheme = entry.categorySchemes?.[index] ?? "";
    if (key !== "") {
      pairs.set(`${scheme}\n${key}`, [key, scheme]);
    }
  }
  return [...pairs.values()];
}

function countUp(counts: Map<string, number>, key: string): void {
  counts.set(key, (counts.get(key) ?? 0) + 1);
}

/** Schemes the articles use for more than one distinct topic; the others join the plain group. */
function groupingSchemes(pairs: readonly [string, string][][]): Set<string> {
  const terms = new Map<string, Set<string>>();
  for (const [key, scheme] of pairs.flat()) {
    terms.set(scheme, (terms.get(scheme) ?? new Set()).add(key));
  }
  return new Set(
    [...terms]
      .filter(([scheme, keys]) => scheme !== "" && keys.size > 1)
      .map(([scheme]) => scheme),
  );
}

/** The scheme that covers more articles, then the later in code-unit order. */
function largerScheme(
  first: string,
  second: string,
  articles: ReadonlyMap<string, number>,
): string {
  const difference = (articles.get(second) ?? 0) - (articles.get(first) ?? 0);
  return difference > 0 || (difference === 0 && second > first)
    ? second
    : first;
}

/**
 * The view's topics in groups, by the scheme the feed gives each (RSS
 * `domain`, Atom `scheme`): topics with no scheme, and those under a scheme
 * the articles use for only one topic, form the plain group; a topic under
 * two schemes goes in the group covering more articles. Groups come in order
 * of how many articles they cover (then by scheme); topics in each by count,
 * then name, as `topicCounts`.
 */
export function topicGroups(entries: readonly FilterEntry[]): TopicCount[][] {
  const pairs = entries.map(schemedTopics);
  const grouping = groupingSchemes(pairs);
  const articles = new Map<string, number>();
  const schemesOf = new Map<string, Set<string>>();
  for (const article of pairs) {
    const grouped = article.map(([key, scheme]): [string, string] => [
      key,
      grouping.has(scheme) ? scheme : "",
    ]);
    for (const used of new Set(grouped.map(([, scheme]) => scheme))) {
      countUp(articles, used);
    }
    for (const [key, scheme] of grouped) {
      schemesOf.set(key, (schemesOf.get(key) ?? new Set()).add(scheme));
    }
  }
  const groups = new Map<string, TopicCount[]>();
  for (const counted of topicCounts(entries)) {
    const schemes = [...(schemesOf.get(topicKey(counted.topic)) ?? [""])];
    const scheme = schemes.reduce((best, other) =>
      largerScheme(best, other, articles),
    );
    groups.set(scheme, [...(groups.get(scheme) ?? []), counted]);
  }
  return [...groups]
    .sort(
      ([first], [second]) =>
        (articles.get(second) ?? 0) - (articles.get(first) ?? 0) ||
        (first < second ? -1 : Number(first > second)),
    )
    .map(([, topics]) => topics);
}

/** Each topic's group and place in it, by its trimmed lower-case form, from `topicGroups`. */
export interface GroupOrder {
  /** Index of the topic's group. */
  group: Map<string, number>;
  /** Place of the topic within its group. */
  rank: Map<string, number>;
}

/** Where each topic sits in the view's groups; built once per view. */
export function groupOrder(groups: readonly TopicCount[][]): GroupOrder {
  const order: GroupOrder = { group: new Map(), rank: new Map() };
  for (const [index, topics] of groups.entries()) {
    for (const [rank, { topic }] of topics.entries()) {
      order.group.set(topicKey(topic), index);
      order.rank.set(topicKey(topic), rank);
    }
  }
  return order;
}

/**
 * An article's topics as rows, one per group of the view it has topics in,
 * in the view's order (`groupOrder`); topics the view does not know form a
 * last row, by name.
 */
export function articleTopicGroups(
  categories: readonly string[],
  order: GroupOrder,
): string[][] {
  const rows = new Map<number, { topic: string; rank: number }[]>();
  const seen = new Set<string>();
  for (const topic of categories) {
    const key = topicKey(topic);
    if (key !== "" && !seen.has(key)) {
      seen.add(key);
      const group = order.group.get(key) ?? Number.POSITIVE_INFINITY;
      const rank = order.rank.get(key) ?? Number.POSITIVE_INFINITY;
      rows.set(group, [...(rows.get(group) ?? []), { topic, rank }]);
    }
  }
  return [...rows]
    .sort(([first], [second]) => first - second)
    .map(([, topics]) =>
      topics
        .sort(
          (first, second) =>
            first.rank - second.rank || compareNames(first.topic, second.topic),
        )
        .map(({ topic }) => topic),
    );
}

/** Whether an article stays after the settings' filters are applied. */
export function passes(settings: ViewSettings, entry: FilterEntry): boolean {
  return keeper(settings)(entry);
}
