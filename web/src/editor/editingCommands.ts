/**
 * The IDE's editor actions in the page editor (Duplicate Line, Delete Line, Move Line, Extend
 * Selection…): the IDE runs them on their own shortcuts (`use-shortcut-of`, so keymap changes
 * apply) and the page carries them out as the IDE editor would.
 */
import { syntaxTree } from "@codemirror/language";
import {
  copyLineDown,
  deleteLine,
  insertBlankLine,
  moveLineDown,
  moveLineUp,
  selectAll,
  selectParentSyntax,
  splitLine,
  toggleComment,
} from "@codemirror/commands";
import { openSearchPanel } from "@codemirror/search";
import { EditorSelection, Prec, StateEffect, StateField, type EditorState, type SelectionRange } from "@codemirror/state";
import { keymap, type Command } from "@codemirror/view";
import { activeEditor } from "@/editor/api";
import { showReplace } from "./findPanel";
import { registerActions, type Action } from "@/platform/actions";

/** The selections Extend Selection grew from, so Shrink Selection can go back. */
const setSelectionStack = StateEffect.define<readonly EditorSelection[]>();

export const selectionStack = StateField.define<readonly EditorSelection[]>({
  create: () => [],
  update(stack, tr) {
    for (const e of tr.effects) if (e.is(setSelectionStack)) return e.value;
    // Any other selection change (typing, clicking) forgets where it grew from.
    return tr.selection || tr.docChanged ? [] : stack;
  },
});

const extendSelection: Command = (view) => {
  const stack = view.state.field(selectionStack, false) ?? [];
  const before = view.state.selection;
  if (!selectParentSyntax(view)) return false;
  view.dispatch({ effects: setSelectionStack.of([...stack, before]) });
  return true;
};

const shrinkSelection: Command = (view) => {
  const stack = view.state.field(selectionStack, false) ?? [];
  const previous = stack[stack.length - 1];
  if (!previous) return false;
  view.dispatch({ selection: previous, effects: setSelectionStack.of(stack.slice(0, -1)) });
  return true;
};

/** Duplicate Line or Selection: the selection after itself, or the caret's line below it. */
const duplicate: Command = (view) => {
  const { state } = view;
  if (state.readOnly) return false;
  if (state.selection.ranges.every((r) => r.empty)) return copyLineDown(view);
  view.dispatch(
    state.changeByRange((r: SelectionRange) => {
      const text = state.sliceDoc(r.from, r.to);
      return { changes: { from: r.to, insert: text }, range: EditorSelection.range(r.to, r.to + text.length) };
    }),
    { userEvent: "input.duplicate", scrollIntoView: true },
  );
  return true;
};

/** A list, task or quote marker at the start of a line, with the indentation before it. */
const LEADING = /^\s*(?:(?:[-*+]|\d+[.)])\s+(?:\[[ xX]\]\s+)?|>\s?)?/;

/** Join Lines: each selected line (or the caret's line) with the next, one space between, as in IDEA. */
const joinLines: Command = (view) => {
  const { state } = view;
  if (state.readOnly) return false;
  const lines = new Set<number>();
  for (const r of state.selection.ranges) {
    const first = state.doc.lineAt(r.from).number;
    const last = Math.max(first + 1, state.doc.lineAt(r.to).number);
    for (let n = first; n < last && n < state.doc.lines; n++) lines.add(n);
  }
  const changes: { from: number; to: number; insert: string }[] = [];
  let end = 0;
  for (const n of [...lines].sort((a, b) => a - b)) {
    const line = state.doc.line(n);
    const next = state.doc.line(n + 1);
    const from = Math.max(end, line.from + line.text.trimEnd().length);
    const to = Math.max(from, next.from + (LEADING.exec(next.text)?.[0].length ?? 0));
    const insert = line.text.trim() && next.text.slice(to - next.from).trim() ? " " : "";
    changes.push({ from, to, insert });
    end = to;
  }
  if (!changes.length) return false;
  view.dispatch({ changes, userEvent: "input.join", scrollIntoView: true });
  return true;
};

/** Start New Line Before Current. */
const startNewLineBefore: Command = (view) => {
  const { state } = view;
  if (state.readOnly) return false;
  view.dispatch(
    state.changeByRange((r) => {
      const line = state.doc.lineAt(r.head);
      return { changes: { from: line.from, insert: state.lineBreak }, range: EditorSelection.cursor(line.from) };
    }),
    { userEvent: "input", scrollIntoView: true },
  );
  return true;
};

/** Toggle Case: upper case unless the text already is, then lower case (the word at a bare caret). */
const toggleCase: Command = (view) => {
  const { state } = view;
  if (state.readOnly) return false;
  view.dispatch(
    state.changeByRange((r) => {
      const w = r.empty ? (state.wordAt(r.head) ?? r) : r;
      const text = state.sliceDoc(w.from, w.to);
      const insert = text === text.toUpperCase() ? text.toLowerCase() : text.toUpperCase();
      return { changes: { from: w.from, to: w.to, insert }, range: EditorSelection.range(w.from, w.to) };
    }),
    { userEvent: "input.case" },
  );
  return true;
};

const replace: Command = (view) => {
  openSearchPanel(view);
  showReplace(view);
  return true;
};

/** Blocks whose content Select All takes first: the lines between their opening and closing marks. */
const SELECT_BLOCKS = new Set(["FencedCode", "CodeBlock", "FrontMatter", "BlockMath"]);

/** The content of the code block, front matter or math block at `pos`, if any. */
export function blockContentAt(state: EditorState, pos: number): { from: number; to: number } | null {
  for (let n: ReturnType<typeof syntaxTree>["topNode"] | null = syntaxTree(state).resolveInner(pos, -1); n; n = n.parent) {
    if (!SELECT_BLOCKS.has(n.name)) continue;
    if (n.name === "CodeBlock") return { from: n.from, to: n.to };
    const first = state.doc.lineAt(n.from);
    const last = state.doc.lineAt(n.to);
    // An unclosed block runs to its end; a closed one stops before its closing line.
    const closed = last.number > first.number && /^\s*(?:`{3,}|~{3,}|-{3,}|\${2})\s*$/.test(last.text);
    const from = Math.min(first.to + 1, state.doc.length);
    const to = closed ? Math.max(from, last.from - 1) : n.to;
    return { from, to };
  }
  return null;
}

/** Select All: the content of the block at the caret first (code, front matter, math), then the document. */
const selectBlockOrAll: Command = (view) => {
  const { state } = view;
  const sel = state.selection.main;
  const block = blockContentAt(state, sel.head);
  if (!block || block.from >= block.to || (sel.from <= block.from && sel.to >= block.to)) return selectAll(view);
  view.dispatch({ selection: EditorSelection.single(block.from, block.to), userEvent: "select" });
  return true;
};

/** Select All on the IDE's shortcut (⌘A / Ctrl+A), which reaches the page. */
export const blockSelectAll = Prec.high(keymap.of([{ key: "Mod-a", run: selectBlockOrAll, preventDefault: true }]));

export const EDITING_COMMANDS: Readonly<Record<string, Command>> = {
  duplicate,
  deleteLine,
  moveLineUp,
  moveLineDown,
  moveStatementUp: moveLineUp,
  moveStatementDown: moveLineDown,
  extendSelection,
  shrinkSelection,
  joinLines,
  startNewLine: insertBlankLine,
  startNewLineBefore,
  splitLine,
  toggleCase,
  commentLine: toggleComment,
  find: openSearchPanel,
  replace,
  selectAll: selectBlockOrAll,
};

/** The commands as page actions `editor.<name>`, run by the IDE (MarkdownStudio.Page.<Name>). */
export function registerEditingCommands(): () => void {
  const actions: Action[] = Object.entries(EDITING_COMMANDS).map(([name, command]) => ({
    id: `editor.${name}`,
    title: name,
    hidden: true,
    when: "editorTextFocus",
    run: () => {
      const view = activeEditor()?.view;
      if (view) command(view);
    },
  }));
  return registerActions(actions);
}
