<script lang="ts">
  import ArrowLeft from "@lucide/svelte/icons/arrow-left";
  import Check from "@lucide/svelte/icons/check";
  import ExternalLink from "@lucide/svelte/icons/external-link";
  import ListFilter from "@lucide/svelte/icons/list-filter";
  import Share from "@lucide/svelte/icons/share-2";
  import Star from "@lucide/svelte/icons/star";
  import Undo2 from "@lucide/svelte/icons/undo-2";
  import { copy } from "#lib/copy.ts";
  import {
    ARTICLE_PULL_DISTANCE,
    type Axis,
    articleSwipe,
    closesByPull,
    pullProgress,
    pullStretch,
    pullTravel,
    swipesPastEnd,
    tick,
    touchAxis,
  } from "#lib/gestures.ts";
  import { isNarrow } from "#lib/layout.ts";
  import { logFailure, type Reader } from "#lib/reader.svelte.ts";
  import { mentionsUrl } from "#lib/sanitize.ts";
  import FilterPopover from "./filter-popover.svelte";
  import FilterSheet from "./filter-sheet.svelte";
  import PullMarker from "./pull-marker.svelte";

  const {
    reader,
    onback,
    onstep,
  }: {
    reader: Reader;
    onback: () => void;
    onstep: (delta: number) => Promise<void>;
  } = $props();

  // how long "Copied" shows
  const COPIED_MS = 2000;
  // how long an article takes to slide off or on, as Android's pager
  const SLIDE_MS = 200;
  // how much an article follows the finger when the list no longer holds it
  const END_RESISTANCE = 0.25;

  const entry = $derived(reader.current);
  // an unread article shows as read while open, since leaving it marks it read
  const shownRead = $derived(entry ? reader.shownRead(entry) : false);
  const filters = $derived(
    entry ? reader.articleFilters(entry) : { sections: [], topics: [] },
  );
  const hasFilters = $derived(
    filters.sections.length > 0 || filters.topics.length > 0,
  );
  const filtersOff = $derived(reader.filtersOff(filters));
  // the filters open over the article: a sheet on a phone, a box below the button on a wide screen
  let filtersOpen = $state<"none" | "sheet" | "box">("none");
  let filterButton: HTMLButtonElement | undefined = $state();
  const showImage = $derived(
    entry !== undefined &&
      entry.imageUrl !== "" &&
      !mentionsUrl(reader.body, entry.imageUrl),
  );
  // the lead picture's own width once loaded, so a small picture is not stretched
  let leadSize = $state({ address: "", width: 0 });
  const leadMaxWidth = $derived(
    leadSize.address !== "" &&
      leadSize.address === entry?.imageUrl &&
      leadSize.width > 0
      ? `${leadSize.width}px`
      : undefined,
  );

  function measureLead(image: HTMLImageElement): () => void {
    function measure(): void {
      leadSize = {
        address: image.getAttribute("src") ?? "",
        width: image.naturalWidth,
      };
    }
    // a cached picture may have loaded before this runs
    if (image.complete) {
      measure();
    }
    image.addEventListener("load", measure);
    return () => image.removeEventListener("load", measure);
  }
  const byline = $derived(
    [
      reader.feeds.find((feed) => feed.id === entry?.feedId)?.title ?? "",
      entry?.author ?? "",
      entry && entry.dated !== false
        ? new Date(entry.published).toLocaleString([], {
            dateStyle: "medium",
            timeStyle: "short",
            hourCycle: "h23",
          })
        : "",
    ]
      .filter((part) => part !== "")
      .join(" · "),
  );
  const canShare = typeof navigator !== "undefined" && "share" in navigator;

  let copied = $state(false);
  let scroller: HTMLDivElement | undefined = $state();
  let startX = 0;
  let startY = 0;
  // whether the touch began where the page leaves sideways swipes to what is under it
  let ignoreSwipe = false;
  // whether, on a phone, the touch began alone with the article at its top or its end
  let startedAtTop = false;
  let startedAtEnd = false;
  // which way the current touch is going, once it is clear
  let axis: Axis = "none";
  // how far the article is moved sideways, in pixels, and whether it is sliding on its own
  let offset = $state(0);
  let sliding = $state(false);
  // how far the finger has pulled the article past its top (positive) or end (negative), and how far the article moved
  let pull = $state(0);
  // whether the pull is sideways, past the first (positive) or last (negative) article, rather than past the top or end
  let pullSideways = $state(false);
  let shift = $state(0);
  // whether the browser asked for reduced motion when the touch began, so a pull leaves the article in place
  let still = false;
  const armed = $derived(Math.abs(pull) >= ARTICLE_PULL_DISTANCE);
  // how far the pull arrow sits from the edge, centered in the gap the pull opens
  const pullGap = $derived(`${Math.abs(pullSideways ? offset : shift) / 2}px`);
  // the arrow points the way of the pull, and turns over once letting go closes the article
  const pullTurn = $derived(
    (pullSideways ? -90 : 0) + (pull > 0 === armed ? 180 : 0),
  );

  /** Where the open article sits in the list; -1 when the list no longer holds it. */
  const index = $derived(
    reader.visible.findIndex((row) => row.id === reader.currentId),
  );

  /** Whether the article is scrolled as far up as it goes. */
  function atTop(box: HTMLElement): boolean {
    return box.scrollTop <= 0;
  }

  /** Whether the article is scrolled as far down as it goes. */
  function atEnd(box: HTMLElement): boolean {
    return box.scrollTop + box.clientHeight >= box.scrollHeight - 1;
  }

  function reducedMotion(): boolean {
    return globalThis.matchMedia("(prefers-reduced-motion: reduce)").matches;
  }

  function wait(milliseconds: number): Promise<void> {
    return new Promise((resolve) => {
      setTimeout(resolve, milliseconds);
    });
  }

  /**
   * Slide the open article off, then open the next or previous one and slide
   * it on from the other side while its text loads, or, past either end of
   * the list, close it. Nothing happens when the article changed or closed
   * during the slide, such as by Back.
   */
  async function slideTo(delta: number, closes: boolean): Promise<void> {
    const width = scroller?.clientWidth ?? globalThis.innerWidth;
    const startId = reader.currentId;
    if (reducedMotion()) {
      offset = 0;
      await onstep(delta);
    } else {
      sliding = true;
      offset = -delta * width;
      await wait(SLIDE_MS);
      sliding = false;
      if (reader.currentId === startId && closes) {
        await onstep(delta);
        offset = 0;
      } else if (reader.currentId === startId) {
        // the next article is chosen at once; its text comes in while it slides on
        const stepping = onstep(delta);
        offset = delta * width;
        // let the new article be drawn off to the side before it slides on
        await wait(16);
        sliding = true;
        offset = 0;
        await wait(SLIDE_MS);
        sliding = false;
        await stepping;
      } else {
        offset = 0;
      }
    }
  }

  async function settle(): Promise<void> {
    sliding = true;
    offset = 0;
    shift = 0;
    await wait(SLIDE_MS);
    sliding = false;
  }

  /**
   * Slide the pulled article on off the screen the way it was pulled
   * (`direction` 1 for down), then close it, unless it changed or closed
   * meanwhile.
   */
  async function pullOff(direction: number): Promise<void> {
    const startId = reader.currentId;
    pull = 0;
    if (!still) {
      sliding = true;
      shift = direction * (scroller?.clientHeight ?? globalThis.innerHeight);
      await wait(SLIDE_MS);
      sliding = false;
    }
    if (reader.currentId === startId) {
      onback();
    }
    shift = 0;
  }

  $effect(() => {
    if (entry?.id && scroller) {
      scroller.scrollTop = 0;
    }
  });

  let shownId: string | undefined;
  $effect(() => {
    // another article closes what the last one had open
    if (entry?.id !== shownId || !hasFilters) {
      shownId = entry?.id;
      filtersOpen = "none";
    }
  });

  function closeFilters(): void {
    filtersOpen = "none";
  }

  async function share(): Promise<void> {
    if (entry) {
      if (canShare) {
        try {
          await navigator.share({ title: entry.title, url: entry.url });
        } catch {
          // the person closed the share sheet
        }
      } else {
        try {
          await navigator.clipboard.writeText(entry.url);
          copied = true;
          setTimeout(() => {
            copied = false;
          }, COPIED_MS);
        } catch {
          copied = false;
        }
      }
    }
  }

  function onStart(event: TouchEvent): void {
    const [touch] = event.touches;
    if (touch && !sliding) {
      startX = touch.clientX;
      startY = touch.clientY;
      axis = "none";
      const { target } = event;
      ignoreSwipe =
        target instanceof Element &&
        target.closest("pre, .table-scroll, code, [data-scrolls]") !== null;
      // a second finger, as in a pinch, or the wide layout never pulls the article closed
      const pulls = event.touches.length === 1 && isNarrow();
      startedAtTop = pulls && scroller !== undefined && atTop(scroller);
      startedAtEnd = pulls && scroller !== undefined && atEnd(scroller);
      still = reducedMotion();
    }
  }

  /** Move the article with a finger `down` pixels from where it began, when that pulls it past its edge. */
  function followPull(event: TouchEvent, down: number): void {
    if (event.touches.length > 1) {
      startedAtTop = false;
      startedAtEnd = false;
    }
    pull =
      axis === "sideways" ? 0 : pullTravel(down, startedAtTop, startedAtEnd);
    shift = still ? 0 : pullStretch(pull);
    // the article moves instead of the browser's own overscroll, which would start on this first move
    if (pull !== 0 && event.cancelable) {
      event.preventDefault();
    }
  }

  /**
   * Move the article with a finger `across` pixels from where it began: with
   * resistance when the list no longer holds it, and past the first or last
   * article as a pull, less and less the further it goes.
   */
  function followSwipe(across: number): void {
    if (index === -1) {
      offset = across * END_RESISTANCE;
    } else if (swipesPastEnd(across, index, reader.visible.length)) {
      pull = across;
      offset = still ? 0 : pullStretch(across);
    } else {
      offset = across;
    }
  }

  /**
   * The article follows a sideways finger, and, pulled on past the edge the
   * touch began at, follows it up or down less and less the further it goes;
   * a pull that crosses the point where letting go closes the article ticks.
   */
  function onMove(event: TouchEvent): void {
    const [touch] = event.touches;
    if (touch && !sliding) {
      const across = touch.clientX - startX;
      const down = touch.clientY - startY;
      if (axis === "none") {
        axis = touchAxis(across, down);
      }
      const wasArmed = armed;
      followPull(event, down);
      pullSideways = axis === "sideways";
      if (axis === "sideways" && !ignoreSwipe) {
        followSwipe(across);
      }
      if (armed !== wasArmed) {
        tick();
      }
    }
  }

  // Svelte's touchmove handlers are passive, so they could not stop the browser's overscroll.
  function listenMoves(box: HTMLElement): () => void {
    box.addEventListener("touchmove", onMove, { passive: false });
    return () => box.removeEventListener("touchmove", onMove);
  }

  /**
   * A sideways swipe steps or closes; on a phone, pulling the article well
   * past the top or end it was already at when the touch began closes it, as
   * Back does.
   */
  function onEnd(event: TouchEvent): void {
    const [touch] = event.changedTouches;
    if (
      touch &&
      event.type === "touchend" &&
      axis === "vertical" &&
      scroller !== undefined &&
      closesByPull(
        touch.clientY - startY,
        startedAtTop && atTop(scroller),
        startedAtEnd && atEnd(scroller),
      )
    ) {
      logFailure(pullOff(Math.sign(touch.clientY - startY)));
    } else if (touch && axis === "sideways" && !ignoreSwipe) {
      const across = touch.clientX - startX;
      const outcome = articleSwipe(across, index, reader.visible.length);
      pull = 0;
      if (outcome === "stay") {
        logFailure(settle());
      } else {
        logFailure(slideTo(across < 0 ? 1 : -1, outcome === "close"));
      }
    } else if (pull !== 0 || shift !== 0) {
      pull = 0;
      logFailure(settle());
    }
    axis = "none";
    startedAtTop = false;
    startedAtEnd = false;
  }
</script>

<article class="relative flex h-full min-h-0 flex-col">
  <!-- on a phone Back and the article's actions sit at the bottom, where a thumb reaches them, as on Android -->
  <header
    class="order-last flex h-[calc(4rem+env(safe-area-inset-bottom))] shrink-0 items-center gap-1 border-t border-line bg-side px-1 pb-[env(safe-area-inset-bottom)] md:hidden"
  >
    <button
      type="button"
      class="grid size-12 place-items-center rounded-md"
      aria-label={copy.back}
      onclick={onback}
    >
      <ArrowLeft class="size-5" />
    </button>
    <span class="flex-1"></span>
    {#if entry}
      {#if hasFilters}
        <button
          type="button"
          class="relative grid size-12 place-items-center rounded-md {filtersOpen ===
          "sheet"
            ? "bg-accent-soft text-accent"
            : "text-muted"}"
          aria-label={filtersOff ? copy.filtersSomeOff : copy.filters}
          aria-expanded={filtersOpen === "sheet"}
          onclick={() => {
            filtersOpen = "sheet";
          }}
        >
          <ListFilter class="size-5" />
          {#if filtersOff}
            <span
              class="pointer-events-none absolute top-2.5 right-2.5 size-2 rounded-full bg-accent ring-2 ring-side"
              aria-hidden="true"
            ></span>
          {/if}
        </button>
      {/if}
      <button
        type="button"
        class="grid size-12 place-items-center rounded-md text-muted"
        aria-label={copy.star}
        aria-pressed={entry.starred}
        onclick={() => logFailure(reader.toggleStar())}
      >
        <Star class="size-5 {entry.starred ? "fill-star text-star" : ""}" />
      </button>
      <button
        type="button"
        class="grid size-12 place-items-center rounded-md text-muted"
        aria-label={shownRead ? copy.markUnread : copy.markRead}
        onclick={() => logFailure(reader.toggleRead())}
      >
        {#if shownRead}
          <Undo2 class="size-5" />
        {:else}
          <Check class="size-5" />
        {/if}
      </button>
      {#if entry.url}
        <button
          type="button"
          class="grid size-12 place-items-center rounded-md text-muted"
          aria-label={copied ? copy.copied : copy.share}
          onclick={share}
        >
          <Share class="size-5" />
        </button>
        <a
          class="grid size-12 place-items-center rounded-md text-muted"
          href={entry.url}
          target="_blank"
          rel="noopener noreferrer"
          aria-label={copy.openOriginal}
        >
          <ExternalLink class="size-5" />
        </a>
      {/if}
    {/if}
  </header>
  <div
    class="min-h-0 flex-1 touch-pan-y touch-pinch-zoom overflow-x-hidden overflow-y-auto px-5 py-8 md:px-12 md:py-10"
    bind:this={scroller}
    role="presentation"
    ontouchstart={onStart}
    {@attach listenMoves}
    ontouchend={onEnd}
    ontouchcancel={onEnd}
  >
    {#if entry}
      <div
        class="mx-auto flex max-w-[44rem] flex-col gap-5 {sliding
          ? "transition-transform duration-200 ease-out"
          : ""}"
        style:transform={offset === 0 && shift === 0
          ? undefined
          : `translate(${offset}px, ${shift}px)`}
      >
        <div class="flex flex-col gap-3">
          <h1
            class="font-reading text-3xl font-bold leading-tight text-balance"
          >
            {#if entry.url}
              <a
                href={entry.url}
                target="_blank"
                rel="noopener noreferrer"
                class="hover:underline"
                >{entry.title}</a
              >
            {:else}
              {entry.title}
            {/if}
          </h1>
          <div class="flex items-center justify-between gap-3">
            <p class="min-w-0 font-mono text-xs text-muted">{byline}</p>
            <div class="relative -my-2 -mr-2 hidden shrink-0 md:flex">
              {#if hasFilters}
                <button
                  type="button"
                  class="relative grid size-8 place-items-center rounded-md hover:bg-side hover:text-ink {filtersOpen ===
                  "box"
                    ? "bg-accent-soft text-accent"
                    : "text-muted"}"
                  aria-label={filtersOff ? copy.filtersSomeOff : copy.filters}
                  aria-expanded={filtersOpen === "box"}
                  title={copy.filters}
                  bind:this={filterButton}
                  onclick={() => {
                    filtersOpen = filtersOpen === "box" ? "none" : "box";
                  }}
                >
                  <ListFilter class="size-4" />
                  {#if filtersOff}
                    <span
                      class="pointer-events-none absolute top-0.5 right-0.5 size-2 rounded-full bg-accent ring-2 ring-page"
                      aria-hidden="true"
                    ></span>
                  {/if}
                </button>
                {#if filtersOpen === "box"}
                  <FilterPopover
                    {reader}
                    {filters}
                    anchor={filterButton}
                    onclose={closeFilters}
                  />
                {/if}
              {/if}
              <button
                type="button"
                class="grid size-8 place-items-center rounded-md text-muted hover:bg-side hover:text-ink"
                aria-label={copy.star}
                aria-pressed={entry.starred}
                title={copy.star}
                onclick={() => logFailure(reader.toggleStar())}
              >
                <Star
                  class="size-4 {entry.starred ? "fill-star text-star" : ""}"
                />
              </button>
              <button
                type="button"
                class="grid size-8 place-items-center rounded-md text-muted hover:bg-side hover:text-ink"
                aria-label={shownRead ? copy.markUnread : copy.markRead}
                title={shownRead ? copy.markUnread : copy.markRead}
                onclick={() => logFailure(reader.toggleRead())}
              >
                {#if shownRead}
                  <Undo2 class="size-4" />
                {:else}
                  <Check class="size-4" />
                {/if}
              </button>
            </div>
          </div>
        </div>
        {#if showImage}
          <img
            src={entry.imageUrl}
            alt=""
            referrerpolicy="no-referrer"
            class="max-h-96 w-full rounded-lg object-cover"
            style:max-width={leadMaxWidth}
            {@attach measureLead}
          >
        {/if}
        <div class="prose max-w-none font-reading prose-img:rounded">
          <!-- biome-ignore lint/nursery/noSvelteAtHtmlTags: the body is sanitized in the reader -->
          {@html reader.body}
        </div>
        <div class="hidden flex-wrap gap-2 text-xs md:flex">
          {#if entry.url}
            <a
              class="flex items-center gap-1.5 rounded-lg border border-line px-3 py-2 hover:bg-side"
              href={entry.url}
              target="_blank"
              rel="noopener noreferrer"
            >
              <ExternalLink class="size-3.5" />
              {copy.openOriginal}
            </a>
            <button
              type="button"
              class="flex items-center gap-1.5 rounded-lg border border-line px-3 py-2 hover:bg-side"
              onclick={share}
            >
              <Share class="size-3.5" />
              {copied ? copy.copied : copy.share}
            </button>
          {/if}
        </div>
      </div>
    {:else}
      <div
        class="flex h-full min-h-40 flex-col items-center justify-center gap-4 p-8 text-sm text-muted"
      >
        <p>{copy.selectEntry}</p>
      </div>
    {/if}
  </div>
  {#if pull !== 0}
    <!-- centered in the gap the pull opens; it turns over once letting go closes the article -->
    <div
      class="pointer-events-none absolute flex items-center justify-center {pullSideways
        ? "top-0 bottom-[calc(4rem+env(safe-area-inset-bottom))] md:bottom-0"
        : "inset-x-0"}"
      style:top={!pullSideways && pull > 0 ? pullGap : undefined}
      style:bottom={!pullSideways && pull < 0
        ? `calc(4rem + env(safe-area-inset-bottom) + ${pullGap})`
        : undefined}
      style:left={pullSideways && pull > 0 ? pullGap : undefined}
      style:right={pullSideways && pull < 0 ? pullGap : undefined}
      aria-hidden="true"
    >
      <PullMarker
        {armed}
        turn={pullTurn}
        style={`opacity: ${pullProgress(pull)}; transform: scale(${0.6 + 0.4 * pullProgress(pull)})`}
      />
    </div>
  {/if}
  <p class="sr-only" aria-live="polite">{armed ? copy.releaseToClose : ""}</p>
  {#if filtersOpen === "sheet" && entry}
    <FilterSheet {reader} {filters} onclose={closeFilters} />
  {/if}
</article>
