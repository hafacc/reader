import type { ExtensionRequest, ExtensionResponse } from "$shared/protocol.ts";
import { extensionId } from "./config.ts";

/** Failed pings in a row before the extension counts as gone; a reload or update of it misses a few. */
const MISSES_BEFORE_GONE = 3;

/**
 * How long the first look for the extension keeps trying before it counts as
 * not installed. Chrome may still be loading its extensions when the app
 * opens, as when it has just started, so the extension can answer a moment late.
 */
export const FIRST_LOOK_MS = 3000;

/** Wait between tries of the first look for the extension. */
export const FIRST_LOOK_RETRY_MS = 500;

function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

function isResponse(value: unknown): value is ExtensionResponse {
  return (
    typeof value === "object" &&
    value !== null &&
    "type" in value &&
    typeof value.type === "string"
  );
}

/** Talks to the Reader browser extension. */
export class ExtensionClient {
  /** Version of the installed extension; empty when there is none. */
  version = $state("");

  /** How long `find` keeps trying. */
  lookMs = FIRST_LOOK_MS;
  /** Wait between tries of `find`. */
  retryMs = FIRST_LOOK_RETRY_MS;
  /** Whether the browser can run the extension; outside Chromium browsers it has no `chrome`. */
  canHaveExtension = typeof chrome !== "undefined";

  private readonly id = extensionId;
  private misses = 0;

  /**
   * Send a request; resolves to undefined when the extension is absent or the
   * message could not be sent (too big, or the extension went away), which is
   * logged except for pings, which fail whenever it is not installed.
   */
  async send(
    request: ExtensionRequest,
  ): Promise<ExtensionResponse | undefined> {
    let response: ExtensionResponse | undefined;
    const runtime = typeof chrome === "undefined" ? undefined : chrome.runtime;
    if (runtime?.sendMessage) {
      try {
        const reply: unknown = await runtime.sendMessage(this.id, request);
        response = isResponse(reply) ? reply : undefined;
      } catch (error) {
        if (request.type !== "ping") {
          console.error(`Reader: could not send ${request.type}:`, error);
        }
        response = undefined;
      }
    }
    return response;
  }

  /**
   * Check whether the extension is reachable now. A known extension is only
   * taken as gone after several failed checks in a row.
   */
  async detect(): Promise<boolean> {
    const response = await this.send({ type: "ping" });
    const found = response?.type === "pong";
    if (found) {
      this.version = response.version;
      this.misses = 0;
    } else {
      this.misses += 1;
      if (this.misses >= MISSES_BEFORE_GONE) {
        this.version = "";
      }
    }
    return found;
  }

  /**
   * Look for the extension as `detect` does, trying again every `retryMs`
   * for up to `lookMs` while it does not answer, for the first look, when
   * Chrome may not have loaded it yet. A browser that cannot run it is tried
   * only once.
   */
  async find(): Promise<boolean> {
    return await this.findBy(
      this.canHaveExtension ? Date.now() + this.lookMs : 0,
    );
  }

  private async findBy(deadline: number): Promise<boolean> {
    let found = await this.detect();
    if (!found && Date.now() + this.retryMs <= deadline) {
      await sleep(this.retryMs);
      found = await this.findBy(deadline);
    }
    return found;
  }
}
