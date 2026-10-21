import { describe, expect, test } from "bun:test";
import type { ExtensionResponse } from "$shared/protocol.ts";
import { ExtensionClient } from "./extension.svelte.ts";

/** A client whose pings are answered from the `nth` on, counting the pings in `pings.count`. */
function lateClient(nth: number): {
  client: ExtensionClient;
  pings: { count: number };
} {
  const client = new ExtensionClient();
  client.canHaveExtension = true;
  const pings = { count: 0 };
  client.send = (): Promise<ExtensionResponse | undefined> => {
    pings.count += 1;
    return Promise.resolve(
      pings.count >= nth ? { type: "pong", version: "2.0.0" } : undefined,
    );
  };
  return { client, pings };
}

describe("finding the extension", () => {
  test("an extension that answers at once is found with one ping", async () => {
    const { client, pings } = lateClient(1);
    expect(await client.find()).toBe(true);
    expect(pings.count).toBe(1);
    expect(client.version).toBe("2.0.0");
  });

  test("an extension that answers late is found on a later try", async () => {
    const { client, pings } = lateClient(3);
    client.lookMs = 1000;
    client.retryMs = 5;
    expect(await client.find()).toBe(true);
    expect(pings.count).toBe(3);
    expect(client.version).toBe("2.0.0");
  });

  test("an extension that never answers counts as missing once the look runs out", async () => {
    const { client, pings } = lateClient(Number.POSITIVE_INFINITY);
    client.lookMs = 100;
    client.retryMs = 30;
    const started = Date.now();
    expect(await client.find()).toBe(false);
    expect(Date.now() - started).toBeLessThan(1000);
    expect(pings.count).toBeGreaterThan(1);
    expect(pings.count).toBeLessThanOrEqual(4);
    expect(client.version).toBe("");
  });

  test("a browser that cannot run the extension is tried once, without waiting", async () => {
    const { client, pings } = lateClient(Number.POSITIVE_INFINITY);
    client.canHaveExtension = false;
    client.lookMs = 60_000;
    client.retryMs = 1;
    expect(await client.find()).toBe(false);
    expect(pings.count).toBe(1);
  });

  test("with no time to wait, the look tries once", async () => {
    const { client, pings } = lateClient(Number.POSITIVE_INFINITY);
    client.lookMs = 0;
    expect(await client.find()).toBe(false);
    expect(pings.count).toBe(1);
  });
});
