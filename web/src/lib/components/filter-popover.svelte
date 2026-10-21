<!-- @component
An open article's filters on a wide screen: a box below its filter button, closed by clicking outside it or Escape. Its parent must be positioned.
-->
<script lang="ts">
  import ListFilter from "@lucide/svelte/icons/list-filter";
  import { copy } from "#lib/copy.ts";
  import type { ArticleFilters, Reader } from "#lib/reader.svelte.ts";
  import FilterRows from "./filter-rows.svelte";

  const {
    reader,
    filters,
    anchor,
    onclose,
  }: {
    reader: Reader;
    /** The article's sections and topics. */
    filters: ArticleFilters;
    /** The button that opened it, whose clicks it leaves alone. */
    anchor: HTMLElement | undefined;
    /** Closes the box. */
    onclose: () => void;
  } = $props();

  let box: HTMLDivElement | undefined = $state();

  function onPointerDown(event: PointerEvent): void {
    const { target } = event;
    if (
      target instanceof Node &&
      !box?.contains(target) &&
      !anchor?.contains(target)
    ) {
      onclose();
    }
  }

  function onKeydown(event: KeyboardEvent): void {
    if (event.key === "Escape") {
      event.preventDefault();
      event.stopPropagation();
      onclose();
      anchor?.focus();
    }
  }
</script>

<svelte:window onpointerdown={onPointerDown} onkeydowncapture={onKeydown} />

<div
  class="absolute top-full right-0 z-20 mt-2 flex max-h-[min(28rem,70vh)] w-[min(28rem,calc(100vw-6rem))] flex-col rounded-xl border border-line bg-panel px-5 pt-3 pb-4 shadow-lg"
  role="dialog"
  aria-label={copy.filters}
  bind:this={box}
>
  <h2 class="flex items-center gap-2 pb-2 text-sm font-semibold">
    <ListFilter class="size-4 text-muted" aria-hidden="true" />
    {copy.filters}
  </h2>
  <div class="min-h-0 overflow-y-auto">
    <FilterRows
      {reader}
      sections={filters.sections}
      topics={filters.topics.map((row) => row.map((topic) => ({ topic })))}
      fromArticle
    />
  </div>
</div>
