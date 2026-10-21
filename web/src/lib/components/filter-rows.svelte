<!-- @component
The filter chips of an article or a view: a row of sections, then one row of topics per group, each a single line that scrolls sideways and runs to the edges of a box padded 1.25rem on each side.
-->
<script lang="ts">
  import { copy } from "#lib/copy.ts";
  import { logFailure, type Reader } from "#lib/reader.svelte.ts";
  import TopicChip from "./topic-chip.svelte";

  const {
    reader,
    sections,
    topics,
    fromArticle,
  }: {
    reader: Reader;
    /** Section chips in order, each with the place turning it off stores; counts shown when given. */
    sections: { label: string; place: string; count?: number }[];
    /** Topic rows in order, one per group; counts shown when given. */
    topics: { topic: string; count?: number }[][];
    /** Whether the rows belong to an open article, whose list then holds its rows. */
    fromArticle: boolean;
  } = $props();

  /** Fade a row's ends where more chips lie past them. */
  function edgeFades(row: HTMLElement): () => void {
    function mark(): void {
      const more = row.scrollWidth - row.clientWidth;
      row.classList.toggle("more-before", more > 1 && row.scrollLeft > 1);
      row.classList.toggle("more-after", more > 1 && row.scrollLeft < more - 1);
    }
    const observer = new ResizeObserver(mark);
    observer.observe(row);
    row.addEventListener("scroll", mark, { passive: true });
    mark();
    return () => {
      observer.disconnect();
      row.removeEventListener("scroll", mark);
    };
  }
</script>

<div class="-mx-5 flex flex-col gap-1 md:gap-2">
  {#if sections.length > 0}
    <!-- data-scrolls lets a page leave sideways swipes that start here to the chips -->
    <ul
      class="chip-row flex gap-2 overflow-x-auto px-5"
      aria-label={copy.sections}
      data-scrolls
      {@attach edgeFades}
    >
      {#each sections as section (section.place)}
        <li class="flex shrink-0 last:pe-5">
          <TopicChip
            section
            topic={section.label}
            count={section.count}
            off={reader.sectionOff(section.place)}
            ontoggle={() =>
              logFailure(reader.toggleSection(section.place, fromArticle))}
          />
        </li>
      {/each}
    </ul>
  {/if}
  {#each topics as row, index (index)}
    <ul
      class="chip-row flex gap-2 overflow-x-auto px-5"
      aria-label={copy.topics}
      data-scrolls
      {@attach edgeFades}
    >
      {#each row as item (item.topic.trim().toLowerCase())}
        <li class="flex shrink-0 last:pe-5">
          <TopicChip
            topic={item.topic}
            count={item.count}
            off={reader.topicOff(item.topic)}
            ontoggle={() =>
              logFailure(reader.toggleTopic(item.topic, fromArticle))}
          />
        </li>
      {/each}
    </ul>
  {/each}
</div>
