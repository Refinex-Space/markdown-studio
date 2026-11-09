import { afterEach, describe, expect, it, vi } from "vitest";
import type { EditorView } from "@codemirror/view";

const theme = vi.hoisted(() => ({ key: "dark", listeners: new Set<() => void>() }));

vi.mock("../mermaid", () => ({
  themeKey: () => theme.key,
  cachedMermaid: () => undefined,
  renderMermaid: (source: string) => Promise.resolve({ svg: `<svg data-theme="${theme.key}">${source}</svg>` }),
  subscribeTheme: (cb: () => void) => {
    theme.listeners.add(cb);
    return () => theme.listeners.delete(cb);
  },
}));

const { MermaidWidget } = await import("./widgets");
const view = { requestMeasure() {}, posAtDOM: () => 0 } as unknown as EditorView;
const settle = () => new Promise((r) => setTimeout(r, 0));
const svgTheme = (dom: HTMLElement) => dom.querySelector(".cm-md-diagram-body > svg")?.getAttribute("data-theme");

describe("MermaidWidget", () => {
  afterEach(() => {
    theme.key = "dark";
    theme.listeners.clear();
  });

  it("redraws in the new theme's colours when the IDE theme changes, and stops once destroyed", async () => {
    const widget = new MermaidWidget("graph LR\n  A --> B", -1);
    const dom = document.body.appendChild(widget.toDOM(view));
    await settle();
    expect(svgTheme(dom)).toBe("dark");

    theme.key = "light";
    theme.listeners.forEach((cb) => cb());
    expect(svgTheme(dom)).toBe("dark");
    await settle();
    expect(svgTheme(dom)).toBe("light");

    widget.destroy(dom);
    expect(theme.listeners.size).toBe(0);
  });
});
