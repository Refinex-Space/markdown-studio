import { afterEach, describe, expect, it } from "vitest";
import { SearchQuery } from "@codemirror/search";
import { findMatches, indexText, openPreviewFind, onPreviewFind, PreviewFind, toRange } from "./previewFind";

function page(html: string): HTMLElement {
  const root = document.createElement("div");
  root.innerHTML = html;
  document.body.append(root);
  return root;
}

const found = (root: HTMLElement, query: SearchQuery) => {
  const index = indexText(root);
  return findMatches(index, query).map((m) => toRange(index, m.from, m.to).toString());
};

describe("finding in the preview", () => {
  afterEach(() => {
    document.body.innerHTML = "";
  });

  it("finds text across inline formatting but not across blocks", () => {
    const root = page("<p>one <strong>two</strong> three</p><p>four</p>");
    expect(found(root, new SearchQuery({ search: "one two thr" }))).toEqual(["one two thr"]);
    expect(found(root, new SearchQuery({ search: "three four" }))).toEqual([]);
    expect(indexText(root).text).toBe("one two three\nfour");
  });

  it("reads soft line breaks as spaces and keeps code as written", () => {
    const root = page("<p>soft\nwrap</p><pre><code>a\nb</code></pre>");
    expect(found(root, new SearchQuery({ search: "soft wrap" }))).toEqual(["soft\nwrap"]);
    expect(found(root, new SearchQuery({ search: "a b" }))).toEqual([]);
  });

  it("follows the case, words and regex toggles", () => {
    const root = page("<p>Cat cat category</p>");
    expect(found(root, new SearchQuery({ search: "cat" }))).toHaveLength(3);
    expect(found(root, new SearchQuery({ search: "cat", caseSensitive: true }))).toHaveLength(2);
    expect(found(root, new SearchQuery({ search: "cat", wholeWord: true }))).toHaveLength(2);
    expect(found(root, new SearchQuery({ search: "c\\w+y", regexp: true }))).toEqual(["category"]);
    expect(found(root, new SearchQuery({ search: "(", regexp: true }))).toEqual([]);
  });

  it("skips controls, formula twins and drawn diagrams' sources", () => {
    const root = page(
      '<div class="code-block"><pre><code>x</code></pre><button>Copy</button></div>' +
        '<span class="katex"><span class="katex-mathml">mathml</span><span class="katex-html">y</span></span>' +
        '<div data-mermaid="" data-mermaid-state="ok"><pre><code>graph</code></pre><div class="mermaid-output"><svg><text>graph</text></svg></div></div>',
    );
    expect(indexText(root).text).toBe("x\ny");
  });
});

describe("the preview's find bar", () => {
  afterEach(() => {
    document.body.innerHTML = "";
  });

  function bar(html: string) {
    const host = document.createElement("div");
    const root = page(html);
    root.tabIndex = -1;
    document.body.prepend(host);
    let closed = 0;
    const find = new PreviewFind(root, host, () => closed++);
    const input = host.querySelector<HTMLInputElement>('input[name="search"]')!;
    const type = (text: string) => {
      input.value = text;
      input.dispatchEvent(new Event("input"));
    };
    const key = (k: string, shiftKey = false) =>
      input.dispatchEvent(new KeyboardEvent("keydown", { key: k, shiftKey, bubbles: true }));
    const count = () => host.querySelector(".studio-find-count")!.textContent;
    return { find, host, root, input, type, key, count, closed: () => closed };
  }

  it("counts matches and steps through them, wrapping around", () => {
    const b = bar("<p>one</p><p>two one</p><p>one</p>");
    b.type("one");
    expect(b.count()).toBe("1/3");
    b.key("Enter");
    expect(b.count()).toBe("2/3");
    b.key("Enter");
    b.key("Enter");
    expect(b.count()).toBe("1/3");
    b.key("Enter", true);
    expect(b.count()).toBe("3/3");
    b.type("none");
    expect(b.count()).toBe("No matches");
    expect(b.host.querySelector(".studio-find")!.classList.contains("studio-find-none")).toBe(true);
    b.find.destroy();
  });

  it("has no replace row: the preview is read only", () => {
    const b = bar("<p>text</p>");
    expect(b.host.querySelector('input[name="replace"]')).toBeNull();
    b.find.destroy();
  });

  it("opens on the selected text and closes with the match selected", () => {
    const b = bar("<p>alpha beta</p><p>beta</p>");
    const text = b.root.querySelector("p")!.firstChild!;
    const sel = window.getSelection()!;
    const r = document.createRange();
    r.setStart(text, 6);
    r.setEnd(text, 10);
    sel.removeAllRanges();
    sel.addRange(r);
    b.find.open();
    expect(b.input.value).toBe("beta");
    expect(document.activeElement).toBe(b.input);
    expect(b.count()).toBe("1/2");
    b.key("Escape");
    expect(b.closed()).toBe(1);
    expect(window.getSelection()!.toString()).toBe("beta");
    expect(document.activeElement).toBe(b.root);
    b.find.destroy();
    expect(b.host.childElementCount).toBe(0);
  });

  it("follows the preview as it re-renders", async () => {
    const b = bar("<p>one</p>");
    b.type("one");
    expect(b.count()).toBe("1/1");
    b.root.innerHTML = "<p>one</p><p>one</p>";
    await new Promise((resolve) => requestAnimationFrame(() => resolve(null)));
    await new Promise((resolve) => setTimeout(resolve, 0));
    expect(b.count()).toBe("1/2");
    b.find.destroy();
  });
});

describe("opening the preview's find bar", () => {
  it("reports whether a preview took it", () => {
    expect(openPreviewFind()).toBe(false);
    let opened = 0;
    const off = onPreviewFind(() => opened++);
    expect(openPreviewFind()).toBe(true);
    expect(opened).toBe(1);
    off();
    expect(openPreviewFind()).toBe(false);
  });
});
