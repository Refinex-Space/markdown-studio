/**
 * Find and Replace in the page editor: the find bar (findBar.ts) as the editor's top panel. The
 * search itself is @codemirror/search's (query, highlighting, stepping, replacing).
 */
import {
  closeSearchPanel,
  findNext,
  findPrevious,
  getSearchQuery,
  replaceAll,
  replaceNext,
  search,
  SearchQuery,
  setSearchQuery,
} from "@codemirror/search";
import type { EditorState, Extension } from "@codemirror/state";
import type { EditorView, Panel, ViewUpdate } from "@codemirror/view";
import { COUNT_LIMIT, FindBar } from "./findBar";

const panels = new WeakMap<EditorView, FindPanel>();

/** The match at the main selection and how many there are (`total` capped at COUNT_LIMIT + 1). */
export function matchPosition(state: EditorState, query: SearchQuery): { current: number; total: number } {
  if (!query.valid || !query.search) return { current: 0, total: 0 };
  const sel = state.selection.main;
  const cursor = query.getCursor(state);
  let total = 0;
  let current = 0;
  for (let r = cursor.next(); !r.done; r = cursor.next()) {
    total++;
    if (r.value.from === sel.from && r.value.to === sel.to) current = total;
    if (total > COUNT_LIMIT) break;
  }
  return { current, total };
}

class FindPanel implements Panel {
  readonly dom: HTMLElement;
  readonly top = true;
  private readonly bar: FindBar;
  private query: SearchQuery;

  constructor(private readonly view: EditorView) {
    this.query = getSearchQuery(view.state);
    this.bar = new FindBar(
      {
        query: (query) => {
          this.query = query;
          this.view.dispatch({ effects: setSearchQuery.of(query) });
        },
        next: () => findNext(this.view),
        previous: () => findPrevious(this.view),
        close: () => {
          closeSearchPanel(this.view);
          this.view.focus();
        },
        replaceNext: () => replaceNext(this.view),
        replaceAll: () => replaceAll(this.view),
      },
      this.query,
    );
    this.dom = this.bar.dom;
    this.refreshCount();
    panels.set(view, this);
  }

  showReplace(show: boolean) {
    this.bar.showReplace(show);
  }

  private refreshCount() {
    const { current, total } = matchPosition(this.view.state, this.query);
    this.bar.setCount(current, total);
  }

  update(u: ViewUpdate) {
    const query = getSearchQuery(u.state);
    if (!query.eq(this.query)) {
      // Changed from outside the panel (opened again on a selection).
      this.query = query;
      this.bar.setQuery(query);
    }
    if (u.docChanged || u.selectionSet || u.transactions.some((tr) => tr.effects.length)) this.refreshCount();
  }

  destroy() {
    this.bar.destroy();
    panels.delete(this.view);
  }
}

export function findPanel(): Extension {
  return search({ top: true, createPanel: (view) => new FindPanel(view) });
}

/** Opens the replace row of the editor's find panel (the panel is open). */
export function showReplace(view: EditorView): void {
  panels.get(view)?.showReplace(true);
}
