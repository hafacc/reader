<script lang="ts">
  import Link from "@lucide/svelte/icons/link";

  const {
    topic,
    off = false,
    count,
    section = false,
    ontoggle,
  }: {
    /** The topic's name, or the section's. */
    topic: string;
    /** Whether the view has the topic turned off, shown struck through. */
    off?: boolean;
    /** How many of the view's articles carry it; not shown when left out. */
    count?: number;
    /** Whether it is a section, led by a link icon since it comes from the article's address. */
    section?: boolean;
    /** Turns the topic off or back on; without it the chip is only a label. */
    ontoggle?: () => void;
  } = $props();
</script>

{#snippet face()}
  <span
    class="flex h-8 items-center gap-1.5 whitespace-nowrap rounded-full border border-line pe-3 text-sm {section
      ? "ps-2.5"
      : "ps-3"} {off ? "text-faded line-through" : "text-ink"}"
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
    aria-pressed={!off}
    onclick={ontoggle}
  >
    {@render face()}
  </button>
{:else}
  {@render face()}
{/if}
