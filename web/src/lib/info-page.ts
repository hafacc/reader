import { siteUrl } from "./config.ts";
import type { InfoPage } from "./types.ts";

/** Whether [detail] is one of the pages that are also web pages of their own. */
export function isInfoPage(detail: string): detail is InfoPage {
  return detail === "privacy" || detail === "terms" || detail === "contact";
}

/**
 * The page a link in one of those pages goes to, when the app has it; links
 * name them by path, or in full on the site.
 */
export function infoPageAt(href: string, origin: string): InfoPage | undefined {
  const url = new URL(href, origin);
  const page = url.pathname.replace(/^\/|\/$/gu, "");
  const onSite = url.origin === origin || url.origin === siteUrl;
  return onSite && isInfoPage(page) ? page : undefined;
}
