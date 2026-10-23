<script lang="ts">
  import CheckCheck from "@lucide/svelte/icons/check-check";
  import ChevronsUpDown from "@lucide/svelte/icons/chevrons-up-down";
  import CircleAlert from "@lucide/svelte/icons/circle-alert";
  import Eye from "@lucide/svelte/icons/eye";
  import EyeOff from "@lucide/svelte/icons/eye-off";
  import RefreshCw from "@lucide/svelte/icons/refresh-cw";
  import SlidersHorizontal from "@lucide/svelte/icons/sliders-horizontal";
  import { copy } from "#lib/copy.ts";
  import {
    ARTICLE_PULL_DISTANCE,
    type Axis,
    pullProgress,
    pullStretch,
    refreshPullTravel,
    tick,
    touchAxis,
  } from "#lib/gestures.ts";
  import { showsListEnd } from "#lib/list-end.ts";
  import { logFailure, type Reader, viewKey } from "#lib/reader.svelte.ts";
  import { articleTopics } from "$shared/filter.ts";
  import DueDot from "./due-dot.svelte";
  import EntryCard from "./entry-card.svelte";
  import Logo from "./logo.svelte";
  import PullMarker from "./pull-marker.svelte";

  const {
    reader,
    settingsOpen,
    shown,
    onopen,
    onmenu,
    onsettings,
  }: {
    reader: Reader;
    settingsOpen: boolean;
    /** Whether the list is on screen; on a phone it is hidden while reading. */
    shown: boolean;
    onopen: () => void;
    onmenu: () => void;
    onsettings: () => void;
  } = $props();

  // how far a pull must go to refresh, and the most the marker moves
  const PULL_DISTANCE = 70;
  const PULL_LIMIT = 120;
  // degrees the marker turns per pixel pulled, and how far it travels per pixel, as Android's pull to refresh
  const PULL_TURN = 3;
  const PULL_FOLLOW = 0.5;
  // where the marker rests while the feeds are checked
  const MARKER_REST = 16;
  // rows drawn at a time
  const PAGE = 60;
  // how long the list takes to spring back from a pull past its end, as an article does
  const SETTLE_MS = 200;
  // where the end marker rests while the feeds are checked: where it stood as letting go began to refresh
  const END_MARKER_REST = pullStretch(ARTICLE_PULL_DISTANCE) / 2;

  let pull = $state(0);
  let scroller: HTMLDivElement | undefined = $state();
  let sentinel: HTMLDivElement | undefined = $state();
  let drawn = $state(PAGE);
  let startX = 0;
  let startY = 0;
  // which way the touch is going, once it is clear
  let axis: Axis = "none";
  // whether the touch began with the list at its top, where a pull may start
  let atTop = false;
  // whether the touch began alone with every row drawn and the list at its end, where a pull up may start
  let atEnd = false;
  // whether the browser asked for reduced motion when the touch began, so a pull past the end leaves the list in place
  let still = false;
  // how far the finger has pulled the list up past its end (negative), how far the list moved, and whether it is springing back
  let endPull = $state(0);
  let endShift = $state(0);
  let settling = $state(false);
  // whether the refresh under way was asked for by a pull past the end, so its marker turns there rather than at the top
  let refreshFromEnd = $state(false);
  const endArmed = $derived(Math.abs(endPull) >= ARTICLE_PULL_DISTANCE);

  /** The feed on show, when the view is one feed. */
  const shownFeed = $derived.by(() => {
    const { selection } = reader;
    return selection.kind === "feed"
      ? reader.feeds.find((feed) => feed.id === selection.id)
      : undefined;
  });

  const feedTitles = $derived(
    new Map(reader.feeds.map((feed) => [feed.id, feed.title])),
  );

  /** Only the first rows are drawn; more come as the end of the list nears. */
  const rows = $derived(reader.visible.slice(0, drawn));

  /** Start again from the top whenever the list is for something else, and when a refresh ends. */
  const listKey = $derived(
    `${viewKey(reader.selection)}|${reader.unreadOnly}|${reader.viewSettings.sort}|${reader.refreshesEnded}`,
  );
  let lastKey = "";

  $effect(() => {
    if (listKey !== lastKey) {
      lastKey = listKey;
      drawn = PAGE;
      if (scroller) {
        scroller.scrollTop = 0;
      }
    }
  });

  /** Draw more rows whenever the end of the list comes within reach. */
  $effect(() => {
    if (scroller && sentinel && drawn < reader.visible.length) {
      const watcher = new IntersectionObserver(
        (seen) => {
          if (seen.some((item) => item.isIntersecting)) {
            drawn += PAGE;
          }
        },
        { root: scroller, rootMargin: "800px" },
      );
      watcher.observe(sentinel);
      return () => watcher.disconnect();
    }
  });

  /** Make sure the open article's row is drawn, so it can be scrolled to. */
  $effect(() => {
    const index = reader.visible.findIndex(
      (entry) => entry.id === reader.currentId,
    );
    if (index >= drawn) {
      drawn = index + PAGE;
    }
  });

  $effect(() => {
    if (!reader.refreshing) {
      refreshFromEnd = false;
    }
  });

  function wait(milliseconds: number): Promise<void> {
    return new Promise((resolve) => {
      setTimeout(resolve, milliseconds);
    });
  }

  /** Whether the list is scrolled as far down as it goes. */
  function scrolledToEnd(box: HTMLElement): boolean {
    return box.scrollTop + box.clientHeight >= box.scrollHeight - 1;
  }

  function onTouchStart(event: TouchEvent): void {
    const [touch] = event.touches;
    if (touch && scroller && !settling) {
      startX = touch.clientX;
      startY = touch.clientY;
      // pulling does nothing while the feeds are checked; their marker keeps turning where it is
      atTop = scroller.scrollTop <= 0 && !reader.refreshing;
      atEnd =
        event.touches.length === 1 &&
        !reader.refreshing &&
        rows.length >= reader.visible.length &&
        scrolledToEnd(scroller);
      still = globalThis.matchMedia("(prefers-reduced-motion: reduce)").matches;
      axis = "none";
    }
  }

  /**
   * Move the list with a finger `down` pixels from where it began, when that
   * pulls it up past the end it was already at, less and less the further it
   * goes, as an article pulled past its end does. A refresh starting midway
   * ends the pull: the list springs back and scrolls as usual.
   */
  function followEndPull(event: TouchEvent, down: number): void {
    if (event.touches.length > 1 || reader.refreshing) {
      atEnd = false;
    }
    const wasArmed = endArmed;
    endPull =
      axis === "sideways"
        ? 0
        : refreshPullTravel(down, false, atEnd, reader.refreshing);
    if (endPull === 0 && reader.refreshing) {
      if (endShift !== 0) {
        logFailure(settle());
      }
    } else if (endPull === 0) {
      endShift = 0;
    } else {
      endShift = still ? 0 : pullStretch(endPull);
      // the list moves instead of the browser's own overscroll
      if (event.cancelable) {
        event.preventDefault();
      }
      if (endArmed !== wasArmed) {
        tick();
      }
    }
  }

  function onTouchMove(event: TouchEvent): void {
    const [touch] = event.touches;
    if (touch && scroller && !settling) {
      const down = touch.clientY - startY;
      if (axis === "none") {
        axis = touchAxis(touch.clientX - startX, down);
      }
      followEndPull(event, down);
      const wasArmed = pull >= PULL_DISTANCE;
      // a pull starts only from the top and only downward; a sideways swipe (a card's), scrolling away from the top or a refresh starting ends it
      const travel = refreshPullTravel(
        down,
        atTop && axis !== "sideways" && scroller.scrollTop <= 0,
        false,
        reader.refreshing,
      );
      if (travel > 0) {
        pull = Math.min(PULL_LIMIT, travel);
      } else {
        pull = 0;
        atTop = false;
      }
      // a tick each time the pull crosses the point where letting go refreshes
      if (pull >= PULL_DISTANCE !== wasArmed && pull > 0) {
        tick();
      }
    }
  }

  // Svelte's touchmove handlers are passive, so they could not stop the browser's overscroll.
  function listenMoves(box: HTMLElement): () => void {
    box.addEventListener("touchmove", onTouchMove, { passive: false });
    return () => box.removeEventListener("touchmove", onTouchMove);
  }

  async function settle(): Promise<void> {
    settling = true;
    endShift = 0;
    await wait(SETTLE_MS);
    settling = false;
  }

  /** Letting go of a pull far enough past the top or the end checks the view's feeds; the list springs back from the end either way. */
  function onTouchEnd(event: TouchEvent): void {
    if (pull >= PULL_DISTANCE) {
      logFailure(reader.refreshView());
    }
    if (endArmed && event.type === "touchend") {
      logFailure(reader.refreshView());
      refreshFromEnd = reader.refreshing;
    }
    if (endShift !== 0) {
      logFailure(settle());
    }
    pull = 0;
    endPull = 0;
    atTop = false;
    atEnd = false;
  }

  $effect(() => {
    const id = reader.currentId;
    if (id && shown) {
      document
        .getElementById(`entry-${id}`)
        ?.scrollIntoView({ block: "nearest" });
    }
  });
</script>

<section class="relative flex h-full min-h-0 flex-col border-line md:border-r">
  <!-- on a phone the bar sits at the bottom, where a thumb reaches it, as on Android -->
  <header
    class="order-last flex h-[calc(4rem+env(safe-area-inset-bottom))] shrink-0 items-center gap-1 border-t border-line bg-side px-1 pb-[env(safe-area-inset-bottom)] text-sm md:order-none md:h-14 md:gap-2 md:border-t-0 md:border-b md:bg-transparent md:px-4 md:pb-0"
  >
    <!-- the logo and the view's name are one button that opens the feed list, as on Android -->
    <button
      type="button"
      class="flex h-12 min-w-0 items-center gap-2 rounded-md pr-1.5 pl-2.5 text-lg font-semibold hover:bg-side md:hidden"
      aria-label={copy.showFeedsFrom(reader.viewTitle)}
      aria-describedby={reader.refreshSuggested ? "due-note" : undefined}
      onclick={onmenu}
    >
      <span class="relative shrink-0" aria-hidden="true">
        <Logo
          class="block size-7"
          loading={!reader.loaded || reader.refreshing || reader.adding}
        />
        {#if reader.refreshSuggested}
          <DueDot />
        {/if}
      </span>
      <span class="truncate">{reader.viewTitle}</span>
      <ChevronsUpDown class="-ml-1 size-4 shrink-0 text-muted" />
    </button>
    {#if reader.refreshSuggested}
      <span id="due-note" class="sr-only">{copy.refreshSuggested}</span>
    {/if}
    <span
      class="hidden whitespace-nowrap font-mono text-xs tabular-nums text-muted md:inline"
      >{copy.unread(reader.viewUnread)}</span
    >
    <span class="flex-1"></span>
    <button
      type="button"
      class="grid size-12 place-items-center rounded-md md:size-8 hover:bg-side {reader.unreadOnly
        ? "text-accent"
        : "text-muted"}"
      aria-label={copy.unreadOnly}
      aria-pressed={reader.unreadOnly}
      title={copy.unreadOnly}
      onclick={() => reader.setUnreadOnly(!reader.unreadOnly)}
    >
      {#if reader.unreadOnly}
        <EyeOff class="size-5 md:size-4" />
      {:else}
        <Eye class="size-5 md:size-4" />
      {/if}
    </button>
    <button
      type="button"
      class="grid size-12 place-items-center rounded-md md:size-8 text-muted hover:bg-side {settingsOpen
        ? "bg-side text-ink"
        : ""}"
      aria-label={copy.settings}
      aria-pressed={settingsOpen}
      title={copy.settings}
      onclick={onsettings}
    >
      <SlidersHorizontal class="size-5 md:size-4" />
    </button>
    <button
      type="button"
      class="grid size-12 place-items-center rounded-md md:size-8 text-muted hover:bg-side disabled:opacity-40 disabled:hover:bg-transparent"
      aria-label={copy.markAllRead}
      title={copy.markAllRead}
      disabled={!reader.canMarkAllRead}
      onclick={() => logFailure(reader.markAllRead())}
    >
      <CheckCheck class="size-5 md:size-4" />
    </button>
  </header>
  <div
    bind:this={scroller}
    role="presentation"
    class="relative min-h-0 flex-1 overflow-y-auto overscroll-y-contain"
    ontouchstart={onTouchStart}
    {@attach listenMoves}
    ontouchend={onTouchEnd}
    ontouchcancel={onTouchEnd}
  >
    {#if pull > 0 || (reader.refreshing && !refreshFromEnd)}
      <!-- the pull marker, which turns with the pull and then spins at the top while the feeds are checked; on a wide screen the sidebar logo shows that instead -->
      <div
        class="pointer-events-none sticky top-0 z-10 flex h-0 justify-center {pull ===
        0
          ? "md:hidden"
          : ""}"
        aria-hidden="true"
      >
        <PullMarker
          armed={pull === 0 || pull >= PULL_DISTANCE}
          class={pull === 0 ? "transition-transform" : ""}
          style={`transform: translateY(${pull === 0 ? MARKER_REST : pull * PULL_FOLLOW - 24}px)`}
        >
          <RefreshCw
            class="size-5 {pull === 0 ? "motion-safe:animate-spin" : ""}"
            style={pull === 0
              ? ""
              : `transform: rotate(${pull * PULL_TURN}deg)`}
          />
        </PullMarker>
      </div>
    {/if}
    <div
      class={settling ? "transition-transform duration-200 ease-out" : ""}
      style:transform={endShift === 0 ? undefined : `translateY(${endShift}px)`}
    >
      {#if shownFeed?.error}
        <p
          class="flex items-center gap-3 border-b border-line bg-danger/10 px-4 py-3 text-sm text-danger"
        >
          <CircleAlert class="size-5 shrink-0" aria-hidden="true" />
          {shownFeed.error}
        </p>
      {/if}
      {#each rows as entry (entry.id)}
        <EntryCard
          {entry}
          read={reader.shownRead(entry)}
          source={feedTitles.get(entry.feedId) ?? ""}
          topics={articleTopics(entry.categories, reader.topicOrder)}
          active={entry.id === reader.currentId}
          onopen={() => {
            logFailure(reader.open(entry.id));
            onopen();
          }}
          ontoggle={() => logFailure(reader.toggleReadFor(entry.id))}
          onstar={() => logFailure(reader.toggleStarFor(entry.id))}
        />
      {:else}
        <p
          class="flex min-h-40 items-center justify-center px-8 py-12 text-center text-sm text-muted"
        >
          {copy.noEntries}
        </p>
      {/each}
      {#if showsListEnd(rows.length, reader.visible.length)}
        <!-- on a phone, pulling up past the end refreshes instead; the button stays there only for screen readers and the keyboard -->
        <!-- sr-only and not-sr-only set width and padding, so they go on a wrapper rather than the button -->
        <footer class="md:pb-10">
          <div class="sr-only focus-within:not-sr-only md:not-sr-only">
            <button
              type="button"
              class="flex min-h-14 w-full items-center justify-center gap-2 border-b border-line bg-page px-4 text-sm text-ink hover:bg-side disabled:text-muted disabled:hover:bg-transparent"
              disabled={reader.refreshing}
              aria-busy={reader.refreshing}
              onclick={() => logFailure(reader.refreshView())}
            >
              <RefreshCw
                class="size-4 {reader.refreshing
                  ? "motion-safe:animate-spin"
                  : ""}"
                aria-hidden="true"
              />
              {copy.refreshButton}
            </button>
          </div>
        </footer>
      {/if}
    </div>
    <div bind:this={sentinel} class="h-px"></div>
  </div>
  {#if endPull !== 0 || (refreshFromEnd && reader.refreshing)}
    <!-- below the list's end, centered in the gap the pull opens; it turns over once letting go refreshes, and then turns there until the feeds are checked -->
    <div
      class="pointer-events-none absolute inset-x-0 flex h-0 items-center justify-center bottom-[calc(4rem+env(safe-area-inset-bottom)+var(--gap))] md:bottom-[var(--gap)] {endPull ===
      0
        ? "md:hidden"
        : ""}"
      style:--gap="{endPull === 0 ? END_MARKER_REST : Math.abs(endShift) / 2}px"
      aria-hidden="true"
    >
      {#if endPull === 0}
        <PullMarker armed={true}>
          <RefreshCw class="size-5 motion-safe:animate-spin" />
        </PullMarker>
      {:else}
        <PullMarker
          armed={endArmed}
          turn={endArmed ? 0 : 180}
          style={`opacity: ${pullProgress(endPull)}; transform: scale(${0.6 + 0.4 * pullProgress(endPull)})`}
        />
      {/if}
    </div>
  {/if}
  <p class="sr-only" aria-live="polite">
    {endArmed ? copy.releaseToRefresh : ""}
  </p>
</section>
