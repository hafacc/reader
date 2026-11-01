<script lang="ts">
  import Link from "@lucide/svelte/icons/link";
  import type { FilterState } from "#lib/reader.svelte.ts";

  const {
    topic,
    state = "on",
    count,
    section = false,
    ontoggle,
  }: {
    /** The topic's name, or the section's. */
    topic: string;
    /** Whether the topic is on, off (struck through) or off in only some feeds (dashed). */
    state?: FilterState;
    /** How many of the view's articles carry it; not shown when left out. */
    count?: number;
    /** Whether it is a section, led by a link icon since it comes from the article's address. */
    section?: boolean;
    /** Turns the topic off or back on; without it the chip is only a label. */
    ontoggle?: () => void;
  } = $props();

  const STYLES: Record<FilterState, string> = {
    on: "border-line text-ink",
    off: "border-line text-faded line-through",
    mixed: "border-dashed border-muted text-muted",
  };
</script>

{#snippet face()}
  <span
    class="flex h-8 items-center gap-1.5 whitespace-nowrap rounded-full border pe-3 text-sm {section
      ? "ps-2.5"
      : "ps-3"} {STYLES[state]}"
  >
    {#if section}
      <Link class="size-3.5 text-muted" aria-hidden="true" />
    {/if}
    {topic}
    {#if count !== undefined}
      <span class="font-mono text-xs tabular-nums text-muted">{count}</span>
    {/if}
  </span>
{/snippet}

{#if ontoggle}
  <!-- on a phone the chip takes taps 44 pixels tall, as Android's chips take 48 dp -->
  <button
    type="button"
    class="flex min-h-11 items-center rounded-full md:min-h-0"
    aria-pressed={state === "mixed" ? "mixed" : state === "on"}
    onclick={ontoggle}
  >
    {@render face()}
  </button>
{:else}
  {@render face()}
{/if}
