<!-- @component
The round marker a pull shows, the same for pulling the list to refresh and for pulling an article closed or the list past its end: in the accent color once letting go acts (`armed`). Given `turn`, it holds an arrow turned that many degrees from pointing down; otherwise what it wraps. Decorative.
-->
<script lang="ts">
  import ArrowDown from "@lucide/svelte/icons/arrow-down";
  import type { Snippet } from "svelte";
  import type { HTMLAttributes } from "svelte/elements";

  const {
    armed,
    turn,
    class: className = "",
    children,
    ...rest
  }: {
    /** Whether letting go now acts. */
    armed: boolean;
    /** How far the arrow is turned, in degrees; no arrow when left out. */
    turn?: number;
    /** Classes added to the marker's own. */
    class?: string;
    /** What the marker holds when it has no arrow. */
    children?: Snippet;
  } & Omit<HTMLAttributes<HTMLSpanElement>, "class" | "children"> = $props();
</script>

<span
  class="grid size-10 place-items-center rounded-full border border-line bg-page shadow-md {armed
    ? "text-accent"
    : "text-muted"} {className}"
  aria-hidden="true"
  {...rest}
>
  {#if turn !== undefined}
    <ArrowDown
      class="size-5 transition-transform duration-150 motion-reduce:transition-none"
      style={`transform: rotate(${turn}deg)`}
    />
  {:else}
    {@render children?.()}
  {/if}
</span>
