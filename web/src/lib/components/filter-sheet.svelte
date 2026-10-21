<!-- @component
An open article's filters on a phone: a sheet from the bottom over the dimmed article, closed by dragging it down, tapping outside it or Escape.
-->
<script lang="ts">
  import ListFilter from "@lucide/svelte/icons/list-filter";
  import { fade, fly } from "svelte/transition";
  import { copy } from "#lib/copy.ts";
  import { type Axis, closesSheet, touchAxis } from "#lib/gestures.ts";
  import type { ArticleFilters, Reader } from "#lib/reader.svelte.ts";
  import FilterRows from "./filter-rows.svelte";

  const {
    reader,
    filters,
    onclose,
  }: {
    reader: Reader;
    /** The article's sections and topics. */
    filters: ArticleFilters;
    /** Closes the sheet. */
    onclose: () => void;
  } = $props();

  // how long the sheet takes to slide in or out, and the scrim to fade
  const MOTION_MS =
    typeof globalThis.matchMedia === "function" &&
    globalThis.matchMedia("(prefers-reduced-motion: reduce)").matches
      ? 0
      : 250;

  let rows: HTMLDivElement | undefined = $state();
  let sheet: HTMLDivElement | undefined = $state();
  // how far the sheet is dragged down, in pixels
  let drag = $state(0);
  let settling = $state(false);
  let startX = 0;
  let startY = 0;
  let startedAtTop = false;
  let axis: Axis = "none";

  $effect(() => {
    sheet?.focus();
  });

  function onStart(event: TouchEvent): void {
    const [touch] = event.touches;
    if (touch) {
      startX = touch.clientX;
      startY = touch.clientY;
      axis = "none";
      settling = false;
      startedAtTop = rows === undefined || rows.scrollTop <= 0;
    }
  }

  function onMove(event: TouchEvent): void {
    const [touch] = event.touches;
    if (touch) {
      const down = touch.clientY - startY;
      if (axis === "none") {
        axis = touchAxis(touch.clientX - startX, down);
      }
      drag = axis === "vertical" && startedAtTop ? Math.max(0, down) : 0;
    }
  }

  function onEnd(): void {
    if (closesSheet(drag, startedAtTop)) {
      onclose();
    } else {
      settling = true;
      drag = 0;
    }
    axis = "none";
  }

  function onKeydown(event: KeyboardEvent): void {
    if (event.key === "Escape") {
      event.preventDefault();
      event.stopPropagation();
      onclose();
    }
  }
</script>

<svelte:window onkeydowncapture={onKeydown} />

<div class="absolute inset-0 z-20 md:hidden">
  <button
    type="button"
    class="absolute inset-0 bg-black/40"
    aria-label={copy.close}
    onclick={onclose}
    transition:fade={{ duration: MOTION_MS }}
  ></button>
  <div
    class="absolute inset-x-0 bottom-0 flex max-h-[78%] flex-col rounded-t-2xl bg-panel px-5 pb-[calc(1.25rem+env(safe-area-inset-bottom))] shadow-[0_-8px_30px_rgb(0_0_0/0.18)] outline-none {settling
      ? "transition-transform duration-200 ease-out"
      : ""}"
    role="dialog"
    aria-modal="true"
    aria-label={copy.filters}
    tabindex="-1"
    style:transform={drag === 0 ? undefined : `translateY(${drag}px)`}
    bind:this={sheet}
    ontouchstart={onStart}
    ontouchmove={onMove}
    ontouchend={onEnd}
    ontouchcancel={onEnd}
    transition:fly={{ y: 400, duration: MOTION_MS }}
  >
    <span
      class="mx-auto my-2.5 h-1 w-9 shrink-0 rounded-full bg-line"
      aria-hidden="true"
    ></span>
    <h2 class="flex items-center gap-2 pb-2 text-base font-semibold">
      <ListFilter class="size-5 text-muted" aria-hidden="true" />
      {copy.filters}
    </h2>
    <div class="min-h-0 overflow-y-auto overscroll-contain" bind:this={rows}>
      <FilterRows
        {reader}
        sections={filters.sections}
        topics={filters.topics.map((row) => row.map((topic) => ({ topic })))}
        fromArticle
      />
    </div>
  </div>
</div>
