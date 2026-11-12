import { afterEach, describe, expect, it } from "vitest";
import { act, cleanup, fireEvent, render, screen } from "@testing-library/react";
import { MarkdownEditor } from "@/features/viewers/markdown/MarkdownEditor";
import { useLiveMarkdown } from "@/features/viewers/markdown/live";
import { useMarkdownModes, type MarkdownMode } from "@/features/viewers/markdown/modes";
import { openPreviewFind } from "../editor/previewFind";
import { PreviewFind } from "./PreviewFind";

const PATH = "/p/find.md";

function show(mode: MarkdownMode, doc: string) {
  act(() => {
    useLiveMarkdown.setState({ [PATH]: { text: doc, topLine: 0, scrollSeq: 0, fontZoom: 0 } });
    useMarkdownModes.setState((s) => ({ byPath: { ...s.byPath, [PATH]: mode } }));
  });
  return render(
    <MarkdownEditor path={PATH} tabId="t" previewBar={<PreviewFind />}>
      <div />
    </MarkdownEditor>,
  );
}

describe("Find over the preview", () => {
  afterEach(() => {
    cleanup();
    useLiveMarkdown.setState({}, true);
  });

  it("opens above the preview and searches the rendered text", () => {
    show("preview", "# Title\n\nSome **bold** text, more text.\n");
    expect(document.querySelector(".studio-find")).toBeNull();
    act(() => void expect(openPreviewFind()).toBe(true));
    const input = screen.getByRole("textbox", { name: "Find" });
    expect(document.activeElement).toBe(input);
    // The bar sits before the preview in its column.
    const preview = screen.getByRole("document", { name: "Markdown preview" });
    expect(input.closest(".host-preview-find")!.nextElementSibling).toBe(preview);
    fireEvent.input(input, { target: { value: "bold text" } });
    expect(document.querySelector(".studio-find-count")!.textContent).toBe("1/1");
    fireEvent.input(input, { target: { value: "text" } });
    expect(document.querySelector(".studio-find-count")!.textContent).toBe("1/2");
    fireEvent.keyDown(input, { key: "Escape" });
    expect(document.querySelector(".studio-find")).toBeNull();
  });

  it("opens on ⌘F / Ctrl+F when the page gets the key", () => {
    show("split", "text\n");
    fireEvent.keyDown(window, { key: "f", ctrlKey: true });
    expect(screen.getByRole("textbox", { name: "Find" })).toBeTruthy();
  });

  it("is not there in Live Preview: its editor has its own", () => {
    show("live", "text\n");
    expect(openPreviewFind()).toBe(false);
    fireEvent.keyDown(window, { key: "f", ctrlKey: true });
    expect(document.querySelector(".studio-find")).toBeNull();
  });
});
