import { describe, expect, it } from "vitest";
import { decodeEntity } from "./liveExtras";

describe("character references in Live Preview", () => {
  it("decodes named and numeric references", () => {
    expect(decodeEntity("&copy;")).toBe("©");
    expect(decodeEntity("&#8594;")).toBe("→");
    expect(decodeEntity("&#x4E2D;")).toBe("中");
    expect(decodeEntity("&notanentity;")).toBeNull();
  });
});
