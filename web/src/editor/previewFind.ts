/**
 * Find in the rendered preview (Preview and Split layouts): the Live Preview's find bar over the
 * preview's text. The text is searched as read, block by block, with @codemirror/search's query
 * (same case, words and regex rules as Live Preview); matches are painted with the CSS Custom
 * Highlight API, so the rendered document is never touched.
 */
import { SearchQuery } from "@codemirror/search";
import { Text } from "@codemirror/state";
import { COUNT_LIMIT, FindBar } from "./findBar";

/** Elements that end a run of text: a match never spans two of them. */
const BLOCKS = new Set([
  "ADDRESS", "ARTICLE", "ASIDE", "BLOCKQUOTE", "BR", "CAPTION", "DD", "DETAILS", "DIV", "DL", "DT",
  "FIGCAPTION", "FIGURE", "FOOTER", "H1", "H2", "H3", "H4", "H5", "H6", "HEADER", "HR", "IMG", "LI",
  "OL", "P", "PRE", "SECTION", "SUMMARY", "TABLE", "TD", "TH", "TR", "UL",
]);

/** What is not read as text: controls, drawings, MathML twins of formulas, hidden diagram sources. */
const SKIP = 'button, svg, script, style, template, [hidden], .katex-mathml, [data-mermaid-state="ok"] > pre';

const HIGHLIGHT = "studio-preview-find";
const HIGHLIGHT_CURRENT = "studio-preview-find-current";

/** The preview's text in reading order, and the text node each part comes from. */
export interface TextIndex {
  text: string;
  nodes: globalThis.Text[];
  /** Offset of each node's text in `text`. */
  starts: number[];
}

export function indexText(root: Element): TextIndex {
  const index: TextIndex = { text: "", nodes: [], starts: [] };
  let pre = 0;
  let pendingBreak = false;
  const visit = (node: Node): void => {
    if (node.nodeType === Node.TEXT_NODE) {
      const data = (node as globalThis.Text).data;
      if (!data) return;
      if (pendingBreak && index.text) index.text += "\n";
      pendingBreak = false;
      index.nodes.push(node as globalThis.Text);
      index.starts.push(index.text.length);
      // Line breaks outside code read as the spaces they render as (same length: offsets hold).
      index.text += pre ? data : data.replace(/[\n\r\t]/g, " ");
      return;
    }
    if (node.nodeType !== Node.ELEMENT_NODE) return;
    const el = node as Element;
    if (el.matches(SKIP)) return;
    const block = BLOCKS.has(el.tagName);
    const isPre = el.tagName === "PRE";
    if (block) pendingBreak = true;
    if (isPre) pre++;
    for (let c = el.firstChild; c; c = c.nextSibling) visit(c);
    if (isPre) pre--;
    if (block) pendingBreak = true;
  };
  for (let c = root.firstChild; c; c = c.nextSibling) visit(c);
  return index;
}

/** Matches of `query` in the text, as offsets (at most COUNT_LIMIT + 1 of them). */
export function findMatches(index: TextIndex, query: SearchQuery): { from: number; to: number }[] {
  if (!query.valid || !query.search || !index.text) return [];
  const cursor = query.getCursor(Text.of(index.text.split("\n")));
  const out: { from: number; to: number }[] = [];
  for (let r = cursor.next(); !r.done; r = cursor.next()) {
    if (r.value.to > r.value.from) out.push(r.value);
    if (out.length > COUNT_LIMIT) break;
  }
  return out;
}

/** The last node starting at or before `pos`. */
function nodeAt(index: TextIndex, pos: number): number {
  let lo = 0;
  let hi = index.starts.length - 1;
  while (lo < hi) {
    const mid = (lo + hi + 1) >> 1;
    if (index.starts[mid]! <= pos) lo = mid;
    else hi = mid - 1;
  }
  return lo;
}

/** The DOM range of `from`–`to` in the text. */
export function toRange(index: TextIndex, from: number, to: number): Range {
  let a = nodeAt(index, from);
  let startOffset = from - index.starts[a]!;
  // On a block break between two nodes: the match starts with the next one.
  if (startOffset > index.nodes[a]!.length && a + 1 < index.nodes.length) {
    a++;
    startOffset = 0;
  }
  const b = nodeAt(index, Math.max(from, to - 1));
  const range = document.createRange();
  range.setStart(index.nodes[a]!, Math.min(startOffset, index.nodes[a]!.length));
  range.setEnd(index.nodes[b]!, Math.min(to - index.starts[b]!, index.nodes[b]!.length));
  return range;
}

const highlights = (): HighlightRegistry | null =>
  typeof CSS !== "undefined" && "highlights" in CSS && typeof Highlight === "function" ? CSS.highlights : null;

const rectOf = (range: Range): DOMRect | null =>
  typeof range.getBoundingClientRect === "function" ? range.getBoundingClientRect() : null;

/** The query the preview's find bar opens with: the last one searched. */
let lastQuery = new SearchQuery({ search: "" });

/** The find bar of one preview (`root`, the element that scrolls), shown in `host`. */
export class PreviewFind {
  readonly bar: FindBar;
  private query = lastQuery;
  private index: TextIndex = { text: "", nodes: [], starts: [] };
  private matches: { from: number; to: number }[] = [];
  private ranges: Range[] = [];
  private current = -1;
  private frame = 0;
  private readonly observer: MutationObserver;

  constructor(
    private readonly root: HTMLElement,
    host: HTMLElement,
    private readonly onClose: () => void,
  ) {
    this.bar = new FindBar(
      {
        query: (query) => {
          this.query = lastQuery = query;
          this.research(true);
        },
        next: () => this.step(1),
        previous: () => this.step(-1),
        close: () => this.close(),
      },
      this.query,
    );
    host.append(this.bar.dom);
    // The preview re-renders (an edit, a diagram drawn): the matches follow, the view stays put.
    this.observer = new MutationObserver(() => {
      if (!this.frame) this.frame = requestAnimationFrame(() => ((this.frame = 0), this.research(false)));
    });
    this.observer.observe(root, { childList: true, subtree: true, characterData: true });
    this.research(true);
  }

  /** Focuses the bar, searching the text selected in the preview if there is a short one. */
  open() {
    const selected = this.selectedText();
    if (selected) {
      const query = new SearchQuery({
        search: selected,
        caseSensitive: this.query.caseSensitive,
        wholeWord: this.query.wholeWord,
      });
      this.query = lastQuery = query;
      this.bar.setQuery(query);
      this.research(true);
    }
    this.bar.focusSearch();
  }

  private selectedText(): string {
    const sel = window.getSelection();
    if (!sel || sel.isCollapsed || !sel.rangeCount || !this.root.contains(sel.getRangeAt(0).commonAncestorContainer)) return "";
    const text = sel.toString().trim();
    return text && text.length <= 200 && !text.includes("\n") ? text : "";
  }

  /** Searches again; `jump`: the current match becomes the first one in view, and is shown. */
  private research(jump: boolean) {
    const previous = this.matches[this.current]?.from ?? -1;
    this.index = indexText(this.root);
    this.matches = findMatches(this.index, this.query);
    this.ranges = this.matches.map((m) => toRange(this.index, m.from, m.to));
    if (!this.ranges.length) this.current = -1;
    else if (jump || previous < 0) this.current = this.firstInView();
    else {
      // The match the reader was on, or the next one.
      const next = this.matches.findIndex((m) => m.from >= previous);
      this.current = next >= 0 ? next : this.matches.length - 1;
    }
    this.paint();
    if (jump && this.current >= 0) this.reveal();
  }

  /** The first match at or below the top of the view (the first one if none is). */
  private firstInView(): number {
    const top = this.root.getBoundingClientRect().top;
    let lo = 0;
    let hi = this.ranges.length;
    while (lo < hi) {
      const mid = (lo + hi) >> 1;
      const rect = rectOf(this.ranges[mid]!);
      if (rect && rect.bottom < top) lo = mid + 1;
      else hi = mid;
    }
    return lo < this.ranges.length ? lo : 0;
  }

  private step(dir: 1 | -1) {
    if (!this.ranges.length) return;
    const n = this.ranges.length;
    this.current = this.current < 0 ? (dir > 0 ? 0 : n - 1) : (this.current + dir + n) % n;
    this.paint();
    this.reveal();
  }

  private paint() {
    const total = this.ranges.length;
    this.bar.setCount(this.current + 1, total);
    const registry = highlights();
    if (!registry) return;
    if (!total) {
      registry.delete(HIGHLIGHT);
      registry.delete(HIGHLIGHT_CURRENT);
      return;
    }
    registry.set(HIGHLIGHT, new Highlight(...this.ranges));
    const current = new Highlight(...(this.current >= 0 ? [this.ranges[this.current]!] : []));
    current.priority = 1;
    registry.set(HIGHLIGHT_CURRENT, current);
  }

  /** Scrolls the current match into view: into a third of the way down when it is off screen. */
  private reveal() {
    const range = this.ranges[this.current];
    const rect = range && rectOf(range);
    if (!range || !rect) return;
    // Code blocks and tables scroll sideways on their own.
    for (let el = range.startContainer.parentElement; el && el !== this.root; el = el.parentElement) {
      if (el.scrollWidth <= el.clientWidth + 1) continue;
      const box = el.getBoundingClientRect();
      if (rect.left < box.left + 16 || rect.right > box.right - 16)
        el.scrollLeft += rect.left - box.left - Math.max(16, (box.width - rect.width) / 2);
    }
    const box = this.root.getBoundingClientRect();
    const margin = Math.min(48, box.height / 4);
    if (rect.top >= box.top + margin && rect.bottom <= box.bottom - margin) return;
    this.root.scrollTo({ top: this.root.scrollTop + rect.top - box.top - box.height / 3, behavior: "smooth" });
  }

  /** Back to reading: the current match stays selected (ready to copy), the preview takes the keys. */
  private close() {
    this.root.focus({ preventScroll: true });
    const range = this.ranges[this.current];
    if (range) {
      const sel = window.getSelection();
      sel?.removeAllRanges();
      sel?.addRange(range.cloneRange());
    }
    this.onClose();
  }

  destroy() {
    this.observer.disconnect();
    cancelAnimationFrame(this.frame);
    highlights()?.delete(HIGHLIGHT);
    highlights()?.delete(HIGHLIGHT_CURRENT);
    this.bar.destroy();
    this.bar.dom.remove();
  }
}

const openers = new Set<() => void>();

/** Opens the find bar of the preview on the page; false when no preview is shown. */
export function openPreviewFind(): boolean {
  openers.forEach((open) => open());
  return openers.size > 0;
}

/** For the preview's find bar: `open` runs on openPreviewFind. */
export function onPreviewFind(open: () => void): () => void {
  openers.add(open);
  return () => openers.delete(open);
}
