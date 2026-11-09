/**
 * The slash menu: typing `/` (or `、`, the same key in Chinese input methods) at the start of
 * a line or after a space opens a menu of Markdown blocks under the caret; what follows the
 * slash filters it. Arrow keys move, Enter or Tab inserts, Escape closes and keeps the text; on
 * a submenu row (callouts, diagrams) Right or Enter opens it, Left or Escape goes back.
 * The menu itself is React (`SlashMenu`), fed by the store this plugin writes.
 */
import { Prec } from "@codemirror/state";
import { closeCompletion } from "@codemirror/autocomplete";
import { EditorView, keymap, ViewPlugin, type ViewUpdate } from "@codemirror/view";
import { create } from "zustand";
import { isFolder, MAX_QUERY, slashGroups, type SlashCommand, type SlashEntry, type SlashFolder } from "./catalog";

let mounted = false;
/** The menu mounts on first use into its own body-level host. */
function mountSlashMenu() {
  if (mounted) return;
  mounted = true;
  void Promise.all([import("react"), import("react-dom/client"), import("./SlashMenu")]).then(([react, dom, m]) => {
    const host = document.createElement("div");
    host.dataset.slashMenu = "";
    document.body.append(host);
    dom.createRoot(host).render(react.createElement(m.SlashMenu));
  });
}

const TRIGGERS = new Set(["/", "、"]);

export interface SlashState {
  view: EditorView | null;
  /** Position of the slash. */
  from: number;
  query: string;
  active: number;
  /** The open submenu (the active row's), and its active row. */
  sub: string | null;
  subActive: number;
  /** Where to show the menu (the slash's screen box). */
  rect: DOMRect | null;
}

export const useSlash = create<SlashState>(() => ({
  view: null,
  from: 0,
  query: "",
  active: 0,
  sub: null,
  subActive: 0,
  rect: null,
}));

/** The menu's rows, top to bottom: commands and submenu rows. */
export const slashItems = (query: string): SlashEntry[] => slashGroups(query).flatMap((g) => g.items);

/** The open submenu, if any. */
export function openFolder(s: SlashState = useSlash.getState()): SlashFolder | null {
  if (!s.sub) return null;
  const entry = slashItems(s.query)[s.active];
  return entry && isFolder(entry) && entry.id === s.sub ? entry : null;
}

export function closeSlash() {
  if (useSlash.getState().view) useSlash.setState({ view: null, rect: null, active: 0, query: "", sub: null, subActive: 0 });
}

/** Runs `item` for the open menu: the `/query` text is replaced by what it inserts. */
export function runSlash(item: SlashCommand) {
  const { view, from } = useSlash.getState();
  if (!view) return;
  const head = view.state.selection.main.head;
  closeSlash();
  item.run(view, { from, to: Math.max(from + 1, head) });
}

function caretRect(view: EditorView, pos: number): DOMRect | null {
  const c = view.coordsAtPos(pos);
  return c ? new DOMRect(c.left, c.top, Math.max(1, c.right - c.left), c.bottom - c.top) : null;
}

/** Whether a `/` typed at `pos` (just inserted) starts a command. */
function opensAt(view: EditorView, pos: number): boolean {
  const line = view.state.doc.lineAt(pos);
  const before = line.text.slice(0, pos - line.from);
  // Line start, after a space, or after list / quote markers.
  return before === "" || /\s$/.test(before);
}

const slashPlugin = ViewPlugin.fromClass(
  class {
    constructor(readonly view: EditorView) {}

    update(u: ViewUpdate) {
      const s = useSlash.getState();
      const mine = s.view === this.view;
      if (u.docChanged) {
        let typed: { pos: number; text: string } | null = null;
        for (const tr of u.transactions) {
          if (!tr.isUserEvent("input.type")) continue;
          tr.changes.iterChanges((_fa, _ta, fromB, _tb, inserted) => {
            const text = inserted.toString();
            if (TRIGGERS.has(text)) typed = { pos: fromB, text };
          });
        }
        if (typed && opensAt(this.view, (typed as { pos: number }).pos)) {
          const pos = (typed as { pos: number }).pos;
          // Not while this update runs: closing completion dispatches.
          const view = this.view;
          queueMicrotask(() => closeCompletion(view));
          useSlash.setState({ view: this.view, from: pos, query: "", active: 0, rect: null });
          mountSlashMenu();
          this.measure(pos);
          return;
        }
      }
      if (!mine) return;
      const from = u.docChanged ? u.changes.mapPos(s.from) : s.from;
      const head = u.state.selection.main.head;
      const line = u.state.doc.lineAt(from);
      const valid =
        u.state.selection.main.empty &&
        head > from &&
        head <= line.to &&
        TRIGGERS.has(u.state.sliceDoc(from, from + 1));
      if (!valid) return closeSlash();
      const query = u.state.sliceDoc(from + 1, head);
      if (query.length > MAX_QUERY || /\s{2}|^\s/.test(query) || (/\s$/.test(query) && !slashItems(query).length))
        return closeSlash();
      if (query !== s.query || from !== s.from)
        useSlash.setState(
          query !== s.query ? { query, from, active: 0, sub: null, subActive: 0 } : { query, from, active: s.active },
        );
      if (u.geometryChanged || u.viewportChanged || from !== s.from) this.measure(from);
    }

    measure(pos: number) {
      this.view.requestMeasure({
        read: (view) => caretRect(view, pos),
        write: (rect) => {
          if (useSlash.getState().view === this.view) useSlash.setState({ rect });
        },
      });
    }

    destroy() {
      if (useSlash.getState().view === this.view) closeSlash();
    }
  },
);

const open = (view: EditorView) => useSlash.getState().view === view;

function move(view: EditorView, d: number): boolean {
  if (!open(view)) return false;
  const folder = openFolder();
  if (folder) {
    const n = folder.items.length;
    useSlash.setState((s) => ({ subActive: (s.subActive + d + n) % n }));
    return true;
  }
  const n = slashItems(useSlash.getState().query).length;
  if (!n) return false;
  useSlash.setState((s) => ({ active: (s.active + d + n) % n, sub: null }));
  return true;
}

/** Opens the submenu of the active row, if it is a submenu row. */
function enter(view: EditorView): boolean {
  if (!open(view) || openFolder()) return false;
  const s = useSlash.getState();
  const entry = slashItems(s.query)[s.active];
  if (!entry || !isFolder(entry)) return false;
  useSlash.setState({ sub: entry.id, subActive: 0 });
  return true;
}

/** Back from a submenu to its row. */
function leave(view: EditorView): boolean {
  if (!open(view) || !openFolder()) return false;
  useSlash.setState({ sub: null, subActive: 0 });
  return true;
}

function accept(view: EditorView): boolean {
  if (!open(view)) return false;
  const s = useSlash.getState();
  const folder = openFolder(s);
  if (folder) {
    const item = folder.items[s.subActive];
    if (item) runSlash(item);
    return true;
  }
  const entry = slashItems(s.query)[s.active];
  if (!entry) {
    closeSlash();
    return false;
  }
  if (isFolder(entry)) return enter(view);
  runSlash(entry);
  return true;
}

const slashKeys = Prec.highest(
  keymap.of([
    { key: "ArrowDown", run: (v) => move(v, 1) },
    { key: "ArrowUp", run: (v) => move(v, -1) },
    { key: "Ctrl-n", run: (v) => move(v, 1) },
    { key: "Ctrl-p", run: (v) => move(v, -1) },
    { key: "ArrowRight", run: enter },
    { key: "ArrowLeft", run: leave },
    { key: "Enter", run: accept },
    { key: "Tab", run: accept },
    {
      key: "Escape",
      run: (v) => {
        if (!open(v)) return false;
        if (!leave(v)) closeSlash();
        return true;
      },
    },
  ]),
);

export const slashMenu = [
  slashPlugin,
  slashKeys,
  EditorView.domEventHandlers({
    blur: (_e, view) => {
      // Clicking the menu keeps focus in the editor (its items don't take focus).
      if (open(view)) setTimeout(() => !view.hasFocus && open(view) && closeSlash(), 0);
    },
    scroll: (_e, view) => {
      if (open(view)) {
        const { from } = useSlash.getState();
        view.requestMeasure({
          read: (v) => caretRect(v, from),
          write: (rect) => open(view) && useSlash.setState({ rect }),
        });
      }
    },
  }),
];
