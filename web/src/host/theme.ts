/**
 * The IDE's look on this page: the core's token set for the scheme (`data-theme`), with the IDE's
 * actual colours and fonts set over it as inline custom properties on the root element (so
 * Mermaid, which watches the root's attributes, redraws its diagrams in the new colours).
 */
import type { ThemePayload } from "./protocol";

let applied: string[] = [];

export function applyTheme(theme: ThemePayload): void {
  const root = document.documentElement;
  if (theme.scheme === "light") root.dataset.theme = "light";
  else delete root.dataset.theme;
  for (const name of applied) if (!(name in theme.tokens)) root.style.removeProperty(name);
  for (const [name, value] of Object.entries(theme.tokens)) root.style.setProperty(name, value);
  applied = Object.keys(theme.tokens);
}
