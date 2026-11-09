import { describe, expect, it } from "vitest";
import { EditorSelection, EditorState } from "@codemirror/state";
import { EditorView } from "@codemirror/view";
import { markdownSupport } from "@/editor/core/cm/markdownLang";
import { blockContentAt, blockSelectAll, EDITING_COMMANDS, selectionStack } from "./editingCommands";
import { runScopeHandlers } from "@codemirror/view";

function editor(doc: string, anchor: number, head = anchor) {
  return new EditorView({
    state: EditorState.create({
      doc,
      selection: EditorSelection.single(anchor, head),
      extensions: [markdownSupport(), selectionStack, blockSelectAll, EditorState.allowMultipleSelections.of(true)],
    }),
  });
}

const selectAllKey = (view: EditorView) =>
  runScopeHandlers(view, new KeyboardEvent("keydown", { key: "a", ...(/Mac/.test(navigator.platform) ? { metaKey: true } : { ctrlKey: true }) }), "editor");
const selected = (view: EditorView) => view.state.sliceDoc(view.state.selection.main.from, view.state.selection.main.to);

const run = (view: EditorView, name: string) => EDITING_COMMANDS[name]!(view);

describe("IDE editor actions in the page", () => {
  it("duplicates the line, or the selection after itself", () => {
    const a = editor("one\ntwo", 1);
    run(a, "duplicate");
    expect(a.state.doc.toString()).toBe("one\none\ntwo");
    const b = editor("one two", 0, 3);
    run(b, "duplicate");
    expect(b.state.doc.toString()).toBe("oneone two");
    expect(b.state.sliceDoc(b.state.selection.main.from, b.state.selection.main.to)).toBe("one");
  });

  it("joins lines, dropping the next line's list marker", () => {
    const v = editor("- first\n- second\n  third", 2);
    run(v, "joinLines");
    expect(v.state.doc.toString()).toBe("- first second\n  third");
    run(v, "joinLines");
    expect(v.state.doc.toString()).toBe("- first second third");
  });

  it("toggles case of the selection or the word at the caret", () => {
    const v = editor("make loud", 1);
    run(v, "toggleCase");
    expect(v.state.doc.toString()).toBe("MAKE loud");
    run(v, "toggleCase");
    expect(v.state.doc.toString()).toBe("make loud");
  });

  it("starts a new line before the current one", () => {
    const v = editor("a\nb", 2);
    run(v, "startNewLineBefore");
    expect(v.state.doc.toString()).toBe("a\n\nb");
    expect(v.state.selection.main.head).toBe(2);
  });

  it("shrinks back through the selections Extend Selection grew", () => {
    const v = editor("Some **bold** text", 8);
    run(v, "extendSelection");
    const first = v.state.selection.main;
    expect(first.empty).toBe(false);
    run(v, "extendSelection");
    expect(v.state.selection.main.to - v.state.selection.main.from).toBeGreaterThan(first.to - first.from);
    run(v, "shrinkSelection");
    expect(v.state.selection.main.eq(first)).toBe(true);
    run(v, "shrinkSelection");
    expect(v.state.selection.main.head).toBe(8);
    expect(run(v, "shrinkSelection")).toBe(false);
  });

  it("moves and deletes lines", () => {
    const v = editor("1\n2\n3", 2);
    run(v, "moveLineUp");
    expect(v.state.doc.toString()).toBe("2\n1\n3");
    run(v, "deleteLine");
    expect(v.state.doc.toString()).toBe("1\n3");
  });

  it("selects a code block's content first, then the document", () => {
    const doc = "Intro\n\n```ts\nconst a = 1;\nconst b = 2;\n```\n\nOutro";
    const v = editor(doc, doc.indexOf("a = 1"));
    selectAllKey(v);
    expect(selected(v)).toBe("const a = 1;\nconst b = 2;");
    selectAllKey(v);
    expect(selected(v)).toBe(doc);
    // The IDE's Select All (MarkdownStudio.Page.SelectAll) runs the same command.
    const w = editor(doc, doc.indexOf("b = 2"));
    expect(EDITING_COMMANDS.selectAll?.(w)).toBe(true);
    expect(selected(w)).toBe("const a = 1;\nconst b = 2;");
  });

  it("selects the front matter's or a math block's content", () => {
    const fm = "---\ntitle: x\ntags: [a]\n---\n\n# Doc";
    const v = editor(fm, fm.indexOf("title"));
    selectAllKey(v);
    expect(selected(v)).toBe("title: x\ntags: [a]");
    const math = "Text\n\n$$\nx^2\n$$\n";
    expect(blockContentAt(EditorState.create({ doc: math, extensions: markdownSupport() }), math.indexOf("x^2"))).toEqual({
      from: math.indexOf("x^2"),
      to: math.indexOf("x^2") + 3,
    });
  });

  it("selects the whole document outside blocks", () => {
    const v = editor("one\ntwo", 1);
    selectAllKey(v);
    expect(selected(v)).toBe("one\ntwo");
  });
});
