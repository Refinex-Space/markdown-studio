import { act, cleanup, render } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { TOOLTIP_DELAY, TooltipHost } from "./Tooltip";

const hover = (el: Element) => el.dispatchEvent(new MouseEvent("pointerover", { bubbles: true }));
const tip = () => document.querySelector('[role="tooltip"]');

describe("TooltipHost", () => {
  afterEach(() => {
    cleanup();
    vi.useRealTimers();
  });

  it("shows a data-tooltip with its shortcut after the hover delay", () => {
    vi.useFakeTimers();
    render(
      <>
        <TooltipHost />
        <button data-tooltip="Bold" data-tooltip-shortcut="⌘B" data-tooltip-placement="top" />
      </>,
    );
    hover(document.querySelector("button")!);
    expect(tip()).toBeNull();
    act(() => void vi.advanceTimersByTime(TOOLTIP_DELAY));
    expect(tip()?.textContent).toBe("Bold⌘B");
  });

  it("takes over a native title (the off-screen browser shows none), keeping it as the name", () => {
    vi.useFakeTimers();
    render(
      <>
        <TooltipHost />
        <button title="Copy Code">
          <svg />
        </button>
      </>,
    );
    const button = document.querySelector("button")!;
    hover(button);
    act(() => void vi.advanceTimersByTime(TOOLTIP_DELAY));
    expect(tip()?.textContent).toBe("Copy Code");
    expect(button.hasAttribute("title")).toBe(false);
    expect(button.getAttribute("aria-label")).toBe("Copy Code");
  });
});
