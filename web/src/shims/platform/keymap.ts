/**
 * Shortcuts come from the IDE keymap: the IDE sends each page action's first keyboard shortcut,
 * already written the platform's way (`⌘B`, `Ctrl+B`), and runs the actions itself when one is
 * pressed. Keys the IDE leaves alone reach the page and its editor keymaps.
 */
import { create } from "zustand";

export const useShortcuts = create<Record<string, string>>(() => ({}));

export function setShortcuts(shortcuts: Record<string, string>): void {
  useShortcuts.setState(shortcuts, true);
}

export function isMacHost(): boolean {
  return typeof navigator !== "undefined" && /Mac|iPhone|iPad/.test(navigator.platform || navigator.userAgent);
}

export function hasPrimaryModifier(e: Pick<KeyboardEvent, "metaKey" | "ctrlKey">): boolean {
  return isMacHost() ? e.metaKey : e.ctrlKey;
}

export function bindingsFor(actionId: string): string[] {
  const s = useShortcuts.getState()[actionId];
  return s ? [s] : [];
}

export function shortcutLabel(actionId: string): string | undefined {
  return useShortcuts.getState()[actionId];
}

const MAC_SYMBOLS: Record<string, string> = { meta: "⌘", ctrl: "⌃", control: "⌃", alt: "⌥", option: "⌥", shift: "⇧" };

/** A core binding (`Meta+C`) for display; shortcuts from the IDE are already formatted and pass through. */
export function formatBinding(binding: string, mac = isMacHost()): string {
  const parts = binding.split("+");
  if (parts.length < 2) return binding;
  if (mac) return parts.map((p) => MAC_SYMBOLS[p.toLowerCase()] ?? p.toUpperCase()).join("");
  return parts.map((p) => (p.toLowerCase() === "meta" ? "Ctrl" : p)).join("+");
}

export type KeyClaim = () => boolean;

/** The core's global keymap yields to an open dialog; here the IDE dispatches keys, so nothing to claim. */
export function registerKeyClaim(_claim: KeyClaim): () => void {
  return () => {};
}
