<script lang="ts" generics="Item">
  import type { Snippet } from "svelte";
  import {
    measureWidth,
    packedRows,
    TOPIC_ROWS,
    wrappedRows,
  } from "#lib/topic-rows.ts";

  const {
    items,
    key,
    chip,
  }: {
    /** What the chips show, in order. */
    items: Item[];
    /** A key telling each item apart, the same while it stays. */
    key: (item: Item) => string;
    /** One item's chip. */
    chip: Snippet<[Item]>;
  } = $props();

  // Tailwind's gap-2 between chips.
  const GAP_PX = 8;
  // Tailwind's p-1 on each side of the scrolling box, which keeps focus rings from being cut off.
  const INSET_PX = 4;

  let boxWidth = $state(0);
  const chipWidths: number[] = $state([]);
  const widths = $derived(
    Array.from(items, (_item, index) => chipWidths[index] ?? 0),
  );
  // Up to TOPIC_ROWS rows wrap as text does; more become that many rows, each chip at the end of the shortest so far, scrolled sideways.
  const packed = $derived(
    wrappedRows(widths, boxWidth - INSET_PX * 2, GAP_PX) > TOPIC_ROWS
      ? packedRows(widths, TOPIC_ROWS, GAP_PX)
      : undefined,
  );
</script>

<!-- data-scrolls lets a page leave sideways swipes that start here to the chips -->
<div
  class="-m-1 overflow-x-auto p-1"
  data-scrolls={packed ? "" : undefined}
  {@attach measureWidth((width) => {
    boxWidth = width;
  })}
>
  <!-- packed chips share one grid column, each offset to its place in its row, so the list keeps the items' order -->
  <ul
    class={packed
      ? "grid w-max justify-items-start gap-2"
      : "flex flex-wrap gap-2"}
  >
    {#each items as item, index (key(item))}
      <li
        class="flex shrink-0"
        style:grid-row={packed ? (packed[index]?.row ?? 0) + 1 : undefined}
        style:grid-column={packed ? 1 : undefined}
        style:margin-left={packed ? `${packed[index]?.left ?? 0}px` : undefined}
        {@attach measureWidth((width) => {
          chipWidths[index] = width;
        })}
      >
        {@render chip(item)}
      </li>
    {/each}
  </ul>
</div>
