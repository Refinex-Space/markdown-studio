/**
 * Find and Replace in the page editor, laid out like the IDE's find bar: one field with the
 * case / words / regex toggles, the match count, previous / next, and a replace row. The search
 * itself is @codemirror/search's (query, highlighting, stepping, replacing).
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
import { tx, useI18n } from "@/platform/i18n";
import { iconMarkup } from "@/ui/icons";

/** Matches counted for the "n/m" label at most (the editor still highlights them all). */
const COUNT_LIMIT = 9_999;

const panels = new WeakMap<EditorView, FindPanel>();

function el<K extends keyof HTMLElementTagNameMap>(tag: K, className: string, attrs: Record<string, string> = {}) {
  const e = document.createElement(tag);
  e.className = className;
  for (const [k, v] of Object.entries(attrs)) e.setAttribute(k, v);
  return e;
}

function iconButton(icon: string, className: string, onClick: () => void): HTMLButtonElement {
  const b = el("button", `studio-find-button ${className}`, { type: "button" });
  b.innerHTML = iconMarkup(icon, 16) ?? "";
  b.addEventListener("mousedown", (e) => e.preventDefault());
  b.addEventListener("click", onClick);
  return b;
}

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
  private readonly search: HTMLInputElement;
  private readonly replace: HTMLInputElement;
  private readonly count: HTMLElement;
  private readonly replaceRow: HTMLElement;
  private readonly expand: HTMLButtonElement;
  private readonly toggles: { name: "caseSensitive" | "wholeWord" | "regexp"; button: HTMLButtonElement; label: string }[];
  private readonly labelled: { node: HTMLElement; attr: "title" | "placeholder" | "text"; label: string }[] = [];
  private query: SearchQuery;
  private unsubscribe: () => void;

  constructor(private readonly view: EditorView) {
    this.query = getSearchQuery(view.state);
    this.dom = el("div", "studio-find");
    this.dom.addEventListener("keydown", (e) => this.keydown(e));

    const findRow = el("div", "studio-find-row");
    this.expand = iconButton("chevron-right", "studio-find-expand", () => this.showReplace(this.replaceRow.hidden !== false));
    this.label(this.expand, "title", "Replace");

    const field = el("div", "studio-find-field");
    field.insertAdjacentHTML("beforeend", iconMarkup("search", 16) ?? "");
    this.search = el("input", "studio-find-input", { "main-field": "true", name: "search", spellcheck: "false", autocomplete: "off" });
    this.search.value = this.query.search;
    this.label(this.search, "placeholder", "Find");
    this.search.addEventListener("input", () => this.commit());
    field.append(this.search);
    this.toggles = [
      { name: "caseSensitive", icon: "case", label: "Match Case" },
      { name: "wholeWord", icon: "word", label: "Words" },
      { name: "regexp", icon: "regex", label: "Regex" },
    ].map((t) => {
      const button = iconButton(t.icon, "studio-find-toggle", () => {
        button.classList.toggle("studio-on");
        button.setAttribute("aria-pressed", String(button.classList.contains("studio-on")));
        this.commit();
      });
      this.label(button, "title", t.label);
      if (this.query[t.name as "caseSensitive" | "wholeWord" | "regexp"]) button.classList.add("studio-on");
      field.append(button);
      return { name: t.name as "caseSensitive" | "wholeWord" | "regexp", button, label: t.label };
    });

    this.count = el("span", "studio-find-count", { "aria-live": "polite" });
    const prev = iconButton("arrow-up", "", () => findPrevious(this.view));
    this.label(prev, "title", "Previous Occurrence");
    const next = iconButton("arrow-down", "", () => findNext(this.view));
    this.label(next, "title", "Next Occurrence");
    const close = iconButton("close", "studio-find-close", () => this.close());
    this.label(close, "title", "Close");
    findRow.append(this.expand, field, this.count, prev, next, close);

    this.replaceRow = el("div", "studio-find-row studio-find-replace-row");
    this.replaceRow.hidden = true;
    const replaceField = el("div", "studio-find-field");
    replaceField.insertAdjacentHTML("beforeend", iconMarkup("replace", 16) ?? "");
    this.replace = el("input", "studio-find-input", { name: "replace", spellcheck: "false", autocomplete: "off" });
    this.replace.value = this.query.replace;
    this.label(this.replace, "placeholder", "Replace");
    this.replace.addEventListener("input", () => this.commit());
    replaceField.append(this.replace);
    const one = el("button", "studio-find-text", { type: "button" });
    this.label(one, "text", "Replace");
    one.addEventListener("click", () => replaceNext(this.view));
    const all = el("button", "studio-find-text", { type: "button" });
    this.label(all, "text", "Replace All");
    all.addEventListener("click", () => replaceAll(this.view));
    this.replaceRow.append(replaceField, one, all);

    this.dom.append(findRow, this.replaceRow);
    this.relabel();
    this.unsubscribe = useI18n.subscribe(() => this.relabel());
    this.refreshCount();
    panels.set(view, this);
  }

  /** Texts in the page's language, now and whenever it changes. */
  private label(node: HTMLElement, attr: "title" | "placeholder" | "text", label: string) {
    this.labelled.push({ node, attr, label });
  }

  private relabel() {
    for (const { node, attr, label } of this.labelled) {
      const text = tx(label);
      if (attr === "text") node.textContent = text;
      else node.setAttribute(attr, text);
      if (attr !== "text") node.setAttribute("aria-label", text);
    }
    this.refreshCount();
  }

  showReplace(show: boolean) {
    this.replaceRow.hidden = !show;
    this.expand.innerHTML = iconMarkup(show ? "chevron-down" : "chevron-right", 16) ?? "";
    this.expand.setAttribute("aria-expanded", String(show));
    (show ? this.replace : this.search).focus();
  }

  private commit() {
    const flags = Object.fromEntries(this.toggles.map((t) => [t.name, t.button.classList.contains("studio-on")]));
    const query = new SearchQuery({ search: this.search.value, replace: this.replace.value, ...flags });
    if (!query.eq(this.query)) {
      this.query = query;
      this.view.dispatch({ effects: setSearchQuery.of(query) });
    }
  }

  private keydown(e: KeyboardEvent) {
    if (e.key === "Escape") {
      e.preventDefault();
      this.close();
    } else if (e.key === "Enter" && e.target === this.search) {
      e.preventDefault();
      (e.shiftKey ? findPrevious : findNext)(this.view);
    } else if (e.key === "Enter" && e.target === this.replace) {
      e.preventDefault();
      (e.metaKey || e.ctrlKey ? replaceAll : replaceNext)(this.view);
    }
  }

  private close() {
    closeSearchPanel(this.view);
    this.view.focus();
  }

  private refreshCount() {
    const { current, total } = matchPosition(this.view.state, this.query);
    this.dom.classList.toggle("studio-find-none", !!this.query.search && total === 0);
    this.count.textContent = !this.query.search
      ? ""
      : total === 0
        ? tx("No matches")
        : `${current || "?"}/${total > COUNT_LIMIT ? `${COUNT_LIMIT}+` : total}`;
  }

  update(u: ViewUpdate) {
    const query = getSearchQuery(u.state);
    if (!query.eq(this.query)) {
      // Changed from outside the panel (opened again on a selection).
      this.query = query;
      this.search.value = query.search;
      this.replace.value = query.replace;
      for (const t of this.toggles) t.button.classList.toggle("studio-on", query[t.name]);
    }
    if (u.docChanged || u.selectionSet || u.transactions.some((tr) => tr.effects.length)) this.refreshCount();
  }

  destroy() {
    this.unsubscribe();
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
