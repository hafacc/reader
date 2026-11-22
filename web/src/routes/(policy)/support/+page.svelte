<script lang="ts">
  import { siteUrl } from "#lib/config.ts";
  import Contact from "../contact/+page.svelte";
</script>

<svelte:head>
  <link rel="canonical" href="{siteUrl}/contact">
</svelte:head>

<Contact />
