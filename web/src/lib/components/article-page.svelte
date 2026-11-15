<script lang="ts">
  import type { Snippet } from "svelte";
  import { PICTURES_PER_ARTICLE, savingData } from "#lib/prefetch.ts";
  import type { Reader } from "#lib/reader.svelte.ts";
  import { mentionsUrl } from "#lib/sanitize.ts";
  import type { Entry } from "#lib/types.ts";

  const {
    reader,
    entry,
    body,
    ahead,
    actions,
    footer,
  }: {
    reader: Reader;
    entry: Entry;
    /** The sanitized body; empty until it is ready. */
    body: string;
    /** Whether the article is drawn beside the open one, ahead of being opened. */
    ahead: boolean;
    /** Buttons beside the byline on a wide screen. */
    actions?: Snippet<[Entry]> | undefined;
    /** Links below the body on a wide screen. */
    footer?: Snippet<[Entry]> | undefined;
  } = $props();

  // drawn ahead while the browser asks to save data, the article holds its pictures until it is opened
  const holdPictures = $derived(ahead && savingData());
  const showImage = $derived(
    !holdPictures &&
      entry.imageUrl !== "" &&
      !mentionsUrl(body, entry.imageUrl),
  );
  // the lead picture's own width once loaded, so a small picture is not stretched
  let leadSize = $state({ address: "", width: 0 });
  const leadMaxWidth = $derived(
    leadSize.address !== "" &&
      leadSize.address === entry.imageUrl &&
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
      reader.feeds.find((feed) => feed.id === entry.feedId)?.title ?? "",
      entry.author,
      entry.dated === false
        ? ""
        : new Date(entry.published).toLocaleString([], {
            dateStyle: "medium",
            timeStyle: "short",
            hourCycle: "h23",
          }),
    ]
      .filter((part) => part !== "")
      .join(" · "),
  );

  let prose: HTMLDivElement | undefined = $state();
  $effect(() => {
    // pictures to the side are outside the view, so the browser would hold back lazy ones until the swipe
    if (ahead && !holdPictures && prose && body !== "") {
      for (const image of Array.from(prose.querySelectorAll("img")).slice(
        0,
        PICTURES_PER_ARTICLE,
      )) {
        image.loading = "eager";
      }
    }
  });
</script>

<div class="flex flex-col gap-3">
  <h1 class="font-reading text-3xl leading-tight font-bold text-balance">
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
    <p class="text-muted min-w-0 font-mono text-xs">{byline}</p>
    <div class="relative -my-2 -mr-2 hidden shrink-0 md:flex">
      {@render actions?.(entry)}
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
<div class="prose font-reading prose-img:rounded max-w-none" bind:this={prose}>
  <!-- biome-ignore lint/nursery/noSvelteAtHtmlTags: the body is sanitized in the reader -->
  {@html body}
</div>
{@render footer?.(entry)}
