import { compareNames } from "./names.ts";

/** One section chip: the words it shows and the place turning it off stores. */
export interface SectionChip {
  /** The folders below those every article of the feed shares, or the site. */
  label: string;
  /** The site and every folder above and at the section, joined by `/`. */
  place: string;
}

/** A section chip with how many articles it covers. */
export interface CountedSection extends SectionChip {
  /** Articles of the view in the section. */
  count: number;
}

/** What section chips are worked out from. */
export interface SectionSource {
  /** Link to the original article. */
  url: string;
  /** Address of the story's source; empty or missing for none. */
  sourceUrl?: string;
}

/** Where an article sits: its site, then the folders of its address that can name a section. */
interface Place {
  domain: string;
  parts: string[];
}

/** An entry of `offTopics` that holds a turned-off section's place rather than a topic. */
export const SECTION_PREFIX = "section:";

const MONTHS = new Set([
  "jan",
  "feb",
  "mar",
  "apr",
  "may",
  "jun",
  "jul",
  "aug",
  "sep",
  "sept",
  "oct",
  "nov",
  "dec",
  "january",
  "february",
  "march",
  "april",
  "june",
  "july",
  "august",
  "september",
  "october",
  "november",
  "december",
]);
const DIGIT = /[0-9]/u;
const PERCENT_RUN = /(?:%[0-9A-Fa-f]{2})+/gu;
const WWW = /^www\./u;
const utf8 = new TextDecoder();

/** Percent escapes decoded as UTF-8, malformed bytes becoming U+FFFD. */
function decodePart(part: string): string {
  return part.replace(PERCENT_RUN, (run) =>
    utf8.decode(
      Uint8Array.from(run.slice(1).split("%"), (hex) =>
        Number.parseInt(hex, 16),
      ),
    ),
  );
}

/** The address sections come from: the story's source when the feed names one, else its link. */
export function sectionAddress(entry: SectionSource): string {
  const source = entry.sourceUrl ?? "";
  return source === "" ? entry.url : source;
}

function placeOf(address: string): Place {
  let place: Place = { domain: "", parts: [] };
  if (URL.canParse(address)) {
    const url = new URL(address);
    const segments = url.pathname
      .split("/")
      .filter((segment) => segment !== "")
      .map(decodePart);
    place = {
      domain: url.hostname.replace(WWW, ""),
      parts: segments
        .slice(0, -1)
        .map((part) => part.toLowerCase())
        .filter((part) => !(DIGIT.test(part) || MONTHS.has(part))),
    };
  }
  return place;
}

function joined(place: Place): string {
  return [place.domain, ...place.parts].join("/");
}

/** Places already worked out, by address, since filters test every article. */
const places = new Map<string, string>();
// Addresses kept before the cache starts over.
const PLACE_LIMIT = 100_000;

/**
 * Where an article sits, as a turned-off section's place is stored: the host
 * without `www.`, then the folders of its address, leaving out its own name,
 * any with a digit and month names; empty when the address cannot be read.
 */
export function articlePlace(entry: SectionSource): string {
  const address = sectionAddress(entry);
  let place = places.get(address);
  if (place === undefined) {
    const found = placeOf(address);
    place = found.domain === "" ? "" : joined(found);
    if (places.size >= PLACE_LIMIT) {
      places.clear();
    }
    places.set(address, place);
  }
  return place;
}

/** Whether `place` is `outer` or lies under it. */
function within(place: string, outer: string): boolean {
  return place === outer || place.startsWith(`${outer}/`);
}

/** Whether any of the stored places holds `place`. */
export function placeHidden(stored: readonly string[], place: string): boolean {
  return place !== "" && stored.some((outer) => within(place, outer));
}

/** The places of the turned-off sections among a view's `offTopics`. */
export function offPlaces(offTopics: readonly string[]): string[] {
  return offTopics
    .filter((topic) => topic.startsWith(SECTION_PREFIX))
    .map((topic) => topic.slice(SECTION_PREFIX.length));
}

/** Whether a section is off: it or a section above it was turned off. */
export function sectionOff(
  offTopics: readonly string[],
  place: string,
): boolean {
  return placeHidden(offPlaces(offTopics), place);
}

/**
 * The `offTopics` after tapping a section: an off section comes back on with
 * every section above it, and turning one off clears the sections below it.
 */
export function toggledSection(
  offTopics: readonly string[],
  place: string,
): string[] {
  const off = sectionOff(offTopics, place);
  const kept = offTopics.filter((topic) => {
    const stored = topic.startsWith(SECTION_PREFIX)
      ? topic.slice(SECTION_PREFIX.length)
      : undefined;
    return (
      stored === undefined ||
      (off ? !within(place, stored) : !within(stored, place))
    );
  });
  return off ? kept : [...kept, `${SECTION_PREFIX}${place}`];
}

/** One folder in the tree of a feed's article folders. */
interface FolderNode {
  articles: number[];
  children: Map<string, FolderNode>;
}

function folderTree(paths: readonly string[][]): FolderNode {
  const root: FolderNode = { articles: [], children: new Map() };
  for (const [index, parts] of paths.entries()) {
    root.articles.push(index);
    let node = root;
    for (const part of parts) {
      let child = node.children.get(part);
      if (child === undefined) {
        child = { articles: [], children: new Map() };
        node.children.set(part, child);
      }
      child.articles.push(index);
      node = child;
    }
  }
  return root;
}

/** Each path cut at its first folder fewer than two articles share under the same parents. */
function sharedPaths(paths: readonly string[][]): string[][] {
  const root = folderTree(paths);
  return paths.map((parts) => {
    const kept: string[] = [];
    let node: FolderNode | undefined = root;
    for (const part of parts) {
      node = node?.children.get(part);
      if (node === undefined || node.articles.length < 2) {
        break;
      }
      kept.push(part);
    }
    return kept;
  });
}

/** Whether every path has the same folder at `depth`. */
function sharesFolder(paths: readonly string[][], depth: number): boolean {
  return (
    paths.length > 0 &&
    paths.every((parts) => parts.length > depth) &&
    new Set(paths.map((parts) => parts[depth])).size === 1
  );
}

/** How many leading folders every path has in common. */
function commonDepth(paths: readonly string[][]): number {
  let depth = 0;
  while (sharesFolder(paths, depth)) {
    depth += 1;
  }
  return depth;
}

/** Section chips by folder, for articles that share one site. */
function folderSections(domain: string, paths: string[][]): SectionChip[][] {
  const result: SectionChip[][] = paths.map(() => []);
  const common = commonDepth(paths);
  function walk(node: FolderNode, depth: number, trail: string[]): void {
    const stops = node.articles.some((index) => paths[index]?.length === depth);
    const splits =
      node.children.size > 1 || (stops && node.children.size === 1);
    for (const [part, child] of node.children) {
      const here = [...trail, part];
      if (splits && depth >= common) {
        const chip = {
          label: here.slice(common).join("/"),
          place: [domain, ...here].join("/"),
        };
        for (const index of child.articles) {
          result[index]?.push(chip);
        }
      }
      walk(child, depth + 1, here);
    }
  }
  walk(folderTree(paths), 0, []);
  return result;
}

/**
 * Each article's section chips, outermost first, worked out from all the
 * articles of one feed.
 *
 * When the articles' addresses (`sectionAddress`) span more than one site,
 * each article's only section is its site. Otherwise sections come from the
 * folders of the addresses (`articlePlace`): a folder counts only when two
 * articles have it under the same parents, folders every article shares are
 * skipped, and each folder that splits the articles gives a chip, so
 * `/us/politics/` gives `us` and `us/politics`.
 */
export function feedSections(
  entries: readonly SectionSource[],
): SectionChip[][] {
  const found = entries.map((entry) => placeOf(sectionAddress(entry)));
  const domains = new Set(
    found.map(({ domain }) => domain).filter((domain) => domain !== ""),
  );
  let result: SectionChip[][];
  if (domains.size > 1) {
    result = found.map(({ domain }) =>
      domain === "" ? [] : [{ label: domain, place: domain }],
    );
  } else {
    const [domain = ""] = domains;
    const usable = found.map((place, index) => ({ place, index }));
    const kept = usable.filter(({ place }) => place.domain !== "");
    const chips = folderSections(
      domain,
      sharedPaths(kept.map(({ place }) => place.parts)),
    );
    result = found.map(() => []);
    for (const [position, { index }] of kept.entries()) {
      result[index] = chips[position] ?? [];
    }
  }
  return result;
}

function compareSections(
  first: CountedSection,
  second: CountedSection,
): number {
  return second.count - first.count || compareNames(first.label, second.label);
}

/** The shorter label, then the first in code-unit order, so every device names a place alike. */
function preferredLabel(first: string, second: string): string {
  let preferred = first;
  if (
    second.length < first.length ||
    (second.length === first.length && second < first)
  ) {
    preferred = second;
  }
  return preferred;
}

/** The nearest section above `place` among the counted ones. */
function parentPlace(
  place: string,
  counted: ReadonlyMap<string, CountedSection>,
): string | undefined {
  let outer = place;
  let parent: string | undefined;
  while (parent === undefined && outer.includes("/")) {
    outer = outer.slice(0, outer.lastIndexOf("/"));
    parent = counted.has(outer) ? outer : undefined;
  }
  return parent;
}

/**
 * A view's section chips with how many of its articles each covers, from
 * each article's chips (`feedSections`): the most articles first, then by
 * name, each followed by the sections under it in the same order.
 */
export function sectionCounts(
  articles: readonly (readonly SectionChip[])[],
): CountedSection[] {
  const counted = new Map<string, CountedSection>();
  for (const chips of articles) {
    for (const { label, place } of new Map(
      chips.map((chip) => [chip.place, chip]),
    ).values()) {
      const known = counted.get(place);
      counted.set(place, {
        label: known === undefined ? label : preferredLabel(known.label, label),
        place,
        count: (known?.count ?? 0) + 1,
      });
    }
  }
  const children = new Map<string | undefined, CountedSection[]>();
  for (const section of counted.values()) {
    const parent = parentPlace(section.place, counted);
    children.set(parent, [...(children.get(parent) ?? []), section]);
  }
  const ordered: CountedSection[] = [];
  function add(parent: string | undefined): void {
    for (const section of (children.get(parent) ?? []).sort(compareSections)) {
      ordered.push(section);
      add(section.place);
    }
  }
  add(undefined);
  return ordered;
}
