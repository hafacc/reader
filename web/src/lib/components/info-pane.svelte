<!-- @component
The privacy policy, the terms or how to reach support, shown in the app, with a link to the same page on the web.
-->
<script lang="ts">
  import ArrowLeft from "@lucide/svelte/icons/arrow-left";
  import ExternalLink from "@lucide/svelte/icons/external-link";
  import X from "@lucide/svelte/icons/x";
  import { copy } from "#lib/copy.ts";
  import { infoPageAt } from "#lib/info-page.ts";
  import type { InfoPage } from "#lib/types.ts";
  import { resolve } from "$app/paths";
  import InfoText from "./info-text.svelte";
  import PhoneBar from "./phone-bar.svelte";

  const {
    page,
    onback,
    onpage,
  }: {
    page: InfoPage;
    onback: () => void;
    /** Show another of these pages, linked from this one. */
    onpage: (page: InfoPage) => void;
  } = $props();

  const titles: Record<InfoPage, string> = {
    privacy: copy.privacyTitle,
    terms: copy.termsTitle,
    contact: copy.contactUs,
  };

  const webPages: Record<InfoPage, string> = {
    privacy: resolve("/(policy)/privacy"),
    terms: resolve("/(policy)/terms"),
    contact: resolve("/(policy)/contact"),
  };

  /** Open a link to another of these pages in the app, and any other link beside it, so the app stays open. */
  function followLinks(text: HTMLElement): () => void {
    text.addEventListener("click", followLink);
    return () => text.removeEventListener("click", followLink);
  }

  function followLink(event: MouseEvent): void {
    const link =
      event.target instanceof Element ? event.target.closest("a") : null;
    const plain =
      event.button === 0 &&
      !(event.metaKey || event.ctrlKey || event.shiftKey || event.altKey);
    if (link && plain) {
      const shown = infoPageAt(link.href, location.origin);
      if (shown) {
        event.preventDefault();
        onpage(shown);
      } else if (!link.href.startsWith("mailto:")) {
        event.preventDefault();
        window.open(link.href, "_blank", "noopener,noreferrer");
      }
    }
  }
</script>

{#snippet webLink(size: string)}
  <a
    class="grid {size} shrink-0 place-items-center rounded-md text-muted hover:bg-side hover:text-ink"
    href={webPages[page]}
    aria-label={copy.openOnWeb}
    title={copy.openOnWeb}
  >
    <ExternalLink class="size-5" />
  </a>
{/snippet}

<section class="flex h-full min-h-0 flex-col">
  <PhoneBar title={titles[page]} label={copy.back} onpress={onback}>
    <ArrowLeft class="size-5" />
    {#snippet end()}
      {@render webLink("size-12")}
    {/snippet}
  </PhoneBar>
  <div class="min-h-0 flex-1 overflow-y-auto px-5 py-8 md:px-12 md:py-10">
    <div class="mx-auto flex max-w-xl flex-col gap-6">
      <!-- on a phone the title is in the bottom bar alone -->
      <div class="hidden items-start justify-between gap-4 md:flex">
        <div class="flex items-center gap-2">
          <h1 class="font-reading text-3xl leading-tight font-bold">
            {titles[page]}
          </h1>
          {@render webLink("size-8")}
        </div>
        <button
          type="button"
          class="text-muted hover:bg-side rounded-md p-1.5"
          aria-label={copy.close}
          onclick={onback}
        >
          <X class="size-5" />
        </button>
      </div>
      <div class="prose" {@attach followLinks}>
        <InfoText {page} />
      </div>
    </div>
  </div>
</section>
