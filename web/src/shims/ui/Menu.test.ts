import { afterEach, describe, expect, it, vi } from "vitest";
import { registerActions } from "@/platform/actions";
import { setContext } from "@/platform/context";
import { receive, setTransport } from "../../host/bridge";
import type { PageMessage } from "../../host/protocol";
import { nativeMenuItems, showContextMenu, type MenuItem } from "./Menu";

describe("context menus as IDE menus", () => {
  afterEach(() => setTransport(null));

  it("resolves actions, collapses separators and numbers nested items", () => {
    setContext("menuTest", true);
    const off = registerActions([{ id: "test.bold", title: "Bold", icon: "md-bold", when: "menuTest", run: () => {} }]);
    const runs = new Map<string, () => void>();
    const items: MenuItem[] = [
      { kind: "separator" },
      { kind: "action", action: "test.bold" },
      { kind: "separator" },
      { kind: "separator" },
      { label: "Insert", submenu: [{ label: "Table", run: () => {} }, { kind: "action", action: "missing" }] },
      { kind: "separator" },
    ];
    const native = nativeMenuItems(items, runs);
    expect(native.map((i) => i.kind)).toEqual(["item", "separator", "item"]);
    expect(native[0]).toMatchObject({ label: "Bold", icon: "md-bold", action: "test.bold", disabled: false });
    expect(native[2]!.children!.map((c) => c.id)).toEqual(["4.0"]);
    expect([...runs.keys()].sort()).toEqual(["1", "4.0"]);
    off();
  });

  it("runs the item the IDE reports as chosen", () => {
    const sent: PageMessage[] = [];
    setTransport((m) => sent.push(m));
    const run = vi.fn();
    showContextMenu({ clientX: 10.4, clientY: 20.6 }, [{ label: "Delete Row", run }]);
    const menu = sent[0] as Extract<PageMessage, { type: "contextMenu" }>;
    expect(menu).toMatchObject({ x: 10, y: 21, editor: false });
    receive({ type: "menuInvoke", menuId: menu.menuId, itemId: menu.items[0]!.id });
    expect(run).toHaveBeenCalledOnce();
  });
});
