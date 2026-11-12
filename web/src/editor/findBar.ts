/**
 * The find bar, laid out like the IDE's: one field with the case / words / regex toggles, the
 * match count, previous / next, and (where the text can be edited) a replace row. It only shows
 * and collects the query; what is searched is up to its owner: the Live Preview editor
 * (findPanel.ts) or the rendered preview (previewFind.ts).
 */
import { SearchQuery } from "@codemirror/search";
import { tx, useI18n } from "@/platform/i18n";
import { iconMarkup } from "@/ui/icons";

/** Matches counted for the "n/m" label at most (the owner still highlights them all). */
export const COUNT_LIMIT = 9_999;

type Flag = "caseSensitive" | "wholeWord" | "regexp";

export interface FindBarHandlers {
  /** The query typed or toggled in the bar. */
  query(query: SearchQuery): void;
  next(): void;
  previous(): void;
  close(): void;
  /** Both given: the bar has a replace row. */
  replaceNext?(): void;
  replaceAll?(): void;
}

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

export class FindBar {
  readonly dom: HTMLElement;
  private readonly search: HTMLInputElement;
  private readonly replace: HTMLInputElement | null = null;
  private readonly count: HTMLElement;
  private readonly replaceRow: HTMLElement | null = null;
  private readonly expand: HTMLButtonElement | null = null;
  private readonly toggles: { name: Flag; button: HTMLButtonElement }[];
  private readonly labelled: { node: HTMLElement; attr: "title" | "placeholder" | "text"; label: string }[] = [];
  private query: SearchQuery;
  private counted = { current: 0, total: 0 };
  private readonly unsubscribe: () => void;

  constructor(
    private readonly on: FindBarHandlers,
    initial: SearchQuery,
  ) {
    this.query = initial;
    const replacing = !!(on.replaceNext && on.replaceAll);
    this.dom = el("div", "studio-find");
    this.dom.addEventListener("keydown", (e) => this.keydown(e));

    const findRow = el("div", "studio-find-row");
    if (replacing) {
      this.expand = iconButton("chevron-right", "studio-find-expand", () => this.showReplace(this.replaceRow?.hidden !== false));
      this.label(this.expand, "title", "Replace");
      findRow.append(this.expand);
    }

    const field = el("div", "studio-find-field");
    field.insertAdjacentHTML("beforeend", iconMarkup("search", 16) ?? "");
    this.search = el("input", "studio-find-input", { "main-field": "true", name: "search", spellcheck: "false", autocomplete: "off" });
    this.search.value = initial.search;
    this.label(this.search, "placeholder", "Find");
    this.search.addEventListener("input", () => this.commit());
    field.append(this.search);
    this.toggles = (
      [
        { name: "caseSensitive", icon: "case", label: "Match Case" },
        { name: "wholeWord", icon: "word", label: "Words" },
        { name: "regexp", icon: "regex", label: "Regex" },
      ] as const
    ).map((t) => {
      const button = iconButton(t.icon, "studio-find-toggle", () => {
        this.setToggle(button, !button.classList.contains("studio-on"));
        this.commit();
      });
      this.label(button, "title", t.label);
      this.setToggle(button, initial[t.name]);
      field.append(button);
      return { name: t.name, button };
    });

    this.count = el("span", "studio-find-count", { "aria-live": "polite" });
    const prev = iconButton("arrow-up", "", () => this.on.previous());
    this.label(prev, "title", "Previous Occurrence");
    const next = iconButton("arrow-down", "", () => this.on.next());
    this.label(next, "title", "Next Occurrence");
    const close = iconButton("close", "studio-find-close", () => this.on.close());
    this.label(close, "title", "Close");
    findRow.append(field, this.count, prev, next, close);
    this.dom.append(findRow);

    if (replacing) {
      this.replaceRow = el("div", "studio-find-row studio-find-replace-row");
      this.replaceRow.hidden = true;
      const replaceField = el("div", "studio-find-field");
      replaceField.insertAdjacentHTML("beforeend", iconMarkup("replace", 16) ?? "");
      this.replace = el("input", "studio-find-input", { name: "replace", spellcheck: "false", autocomplete: "off" });
      this.replace.value = initial.replace;
      this.label(this.replace, "placeholder", "Replace");
      this.replace.addEventListener("input", () => this.commit());
      replaceField.append(this.replace);
      const one = el("button", "studio-find-text", { type: "button" });
      this.label(one, "text", "Replace");
      one.addEventListener("click", () => this.on.replaceNext?.());
      const all = el("button", "studio-find-text", { type: "button" });
      this.label(all, "text", "Replace All");
      all.addEventListener("click", () => this.on.replaceAll?.());
      this.replaceRow.append(replaceField, one, all);
      this.dom.append(this.replaceRow);
    }

    this.relabel();
    this.unsubscribe = useI18n.subscribe(() => this.relabel());
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
    this.renderCount();
  }

  private setToggle(button: HTMLButtonElement, on: boolean) {
    button.classList.toggle("studio-on", on);
    button.setAttribute("aria-pressed", String(on));
  }

  private commit() {
    const flags = Object.fromEntries(this.toggles.map((t) => [t.name, t.button.classList.contains("studio-on")]));
    const query = new SearchQuery({ search: this.search.value, replace: this.replace?.value ?? "", ...flags });
    if (!query.eq(this.query)) {
      this.query = query;
      this.on.query(query);
    }
  }

  private keydown(e: KeyboardEvent) {
    if (e.key === "Escape") {
      e.preventDefault();
      this.on.close();
    } else if (e.key === "Enter" && e.target === this.search) {
      e.preventDefault();
      (e.shiftKey ? this.on.previous : this.on.next).call(this.on);
    } else if (e.key === "Enter" && e.target === this.replace) {
      e.preventDefault();
      (e.metaKey || e.ctrlKey ? this.on.replaceAll : this.on.replaceNext)?.call(this.on);
    }
  }

  /** The query set from outside the bar (opened again on a selection): shown without being reported. */
  setQuery(query: SearchQuery) {
    if (query.eq(this.query)) return;
    this.query = query;
    this.search.value = query.search;
    if (this.replace) this.replace.value = query.replace;
    for (const t of this.toggles) this.setToggle(t.button, query[t.name]);
    this.renderCount();
  }

  /** The match at the caret (0: none) and how many there are (`total` above COUNT_LIMIT: "9999+"). */
  setCount(current: number, total: number) {
    this.counted = { current, total };
    this.renderCount();
  }

  private renderCount() {
    const { current, total } = this.counted;
    const searching = !!this.query.search;
    this.dom.classList.toggle("studio-find-none", searching && total === 0);
    this.count.textContent = !searching
      ? ""
      : total === 0
        ? tx("No matches")
        : `${current || "?"}/${total > COUNT_LIMIT ? `${COUNT_LIMIT}+` : total}`;
  }

  /** Focuses the search field, its text selected for typing over. */
  focusSearch() {
    this.search.focus();
    this.search.select();
  }

  showReplace(show: boolean) {
    if (!this.replaceRow || !this.expand || !this.replace) return;
    this.replaceRow.hidden = !show;
    this.expand.innerHTML = iconMarkup(show ? "chevron-down" : "chevron-right", 16) ?? "";
    this.expand.setAttribute("aria-expanded", String(show));
    (show ? this.replace : this.search).focus();
  }

  destroy() {
    this.unsubscribe();
  }
}
