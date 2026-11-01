<!-- @component
A page's text as both its web page and the app show it: the privacy policy, the terms, or how to reach support.
-->
<script lang="ts">
  import { feedbackUrl, supportEmail } from "#lib/config.ts";
  import { copy } from "#lib/copy.ts";
  import type { InfoPage } from "#lib/types.ts";
  import privacy from "$policy/privacy.html?raw";
  import terms from "$policy/terms.html?raw";

  const { page }: { page: InfoPage } = $props();

  // the policies are written once, in shared/policy, for the Android app too
  const policies = { privacy, terms };
</script>

{#if page === "contact"}
  <p>
    {copy.contactBody}
    <a href="mailto:{supportEmail}">{supportEmail}</a>
  </p>
  <p>
    {copy.contactFeedback}
    <a href={feedbackUrl}>{copy.contactFeedbackLink}</a>.
  </p>
  <p>
    {copy.contactDelete}
    <a href="/delete">{copy.contactDeleteLink}</a>. {copy.contactPrivacy}
    <a href="/privacy">{copy.contactPrivacyLink}</a>.
  </p>
{:else}
  <!-- biome-ignore lint/nursery/noSvelteAtHtmlTags: our own text from shared/policy, not a feed's -->
  {@html policies[page]}
{/if}
