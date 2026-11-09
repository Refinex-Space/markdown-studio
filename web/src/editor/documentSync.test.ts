import { afterEach, describe, expect, it } from "vitest";
import { ChangeSet, EditorState, Text } from "@codemirror/state";
import { EditorView } from "@codemirror/view";
import { setTransport } from "../host/bridge";
import type { PageMessage, WireUpdate } from "../host/protocol";
import { documentSync, receive } from "./documentSync";

/** The IDE side, as `DocumentAuthority.kt` behaves: versioned, rejects stale pushes, answers every push. */
class Authority {
  doc: Text;
  version = 0;
  constructor(text: string) {
    this.doc = Text.of(text.split("\n"));
  }
  /** A change made in the IDE (Source editor, undo, VCS). */
  edit(from: number, to: number, insert: string): WireUpdate {
    const changes = ChangeSet.of({ from, to, insert }, this.doc.length);
    this.doc = changes.apply(this.doc);
    this.version++;
    return { changes: changes.toJSON() as WireUpdate["changes"], clientID: "ide" };
  }
  push(p: Extract<PageMessage, { type: "push" }>): { version: number; updates: WireUpdate[] } {
    if (p.version !== this.version) return { version: this.version, updates: [] };
    for (const u of p.updates) this.doc = ChangeSet.fromJSON(u.changes).apply(this.doc);
    this.version += p.updates.length;
    return { version: this.version, updates: p.updates };
  }
}

const flush = () => new Promise((r) => setTimeout(r, 0));

function setup(text: string) {
  const ide = new Authority(text);
  const pushes: Extract<PageMessage, { type: "push" }>[] = [];
  setTransport((m) => {
    if (m.type === "push") pushes.push(m);
  });
  const view = new EditorView({ state: EditorState.create({ doc: text, extensions: documentSync(0) }) });
  return { ide, pushes, view };
}

describe("document sync with the IDE", () => {
  afterEach(() => setTransport(null));

  it("pushes typing and settles once the IDE confirms it", async () => {
    const { ide, pushes, view } = setup("Hello");
    view.dispatch({ changes: { from: 5, insert: " world" }, userEvent: "input.type" });
    await flush();
    expect(pushes).toHaveLength(1);
    expect(pushes[0]!.userEvent).toBe("input.type");
    const answer = ide.push(pushes[0]!);
    expect(receive(view, answer.version, answer.updates)).toBe(true);
    await flush();
    expect(ide.doc.toString()).toBe("Hello world");
    expect(pushes).toHaveLength(1);
  });

  it("applies IDE changes without pushing them back", async () => {
    const { ide, pushes, view } = setup("abc");
    const u = ide.edit(0, 1, "X");
    receive(view, ide.version, [u]);
    await flush();
    expect(view.state.doc.toString()).toBe("Xbc");
    expect(pushes).toHaveLength(0);
  });

  it("rebases typing over a concurrent IDE change and converges", async () => {
    const { ide, pushes, view } = setup("one two");
    view.dispatch({ changes: { from: 7, insert: "!" }, userEvent: "input.type" });
    await flush();
    // The IDE changed the start meanwhile: the push is stale.
    const concurrent = ide.edit(0, 3, "ONE");
    const rejected = ide.push(pushes[0]!);
    expect(rejected.updates).toHaveLength(0);
    receive(view, ide.version, [concurrent]);
    receive(view, rejected.version, rejected.updates);
    await flush();
    expect(pushes).toHaveLength(2);
    const accepted = ide.push(pushes[1]!);
    receive(view, accepted.version, accepted.updates);
    expect(ide.doc.toString()).toBe("ONE two!");
    expect(view.state.doc.toString()).toBe("ONE two!");
  });

  it("asks for a reset when updates skip versions", () => {
    const { ide, view } = setup("abc");
    ide.edit(0, 0, "1");
    const second = ide.edit(0, 0, "2");
    expect(receive(view, ide.version, [second])).toBe(false);
  });
});
