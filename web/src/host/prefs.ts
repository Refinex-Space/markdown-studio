/**
 * Page options from Settings | Tools | Markdown Studio: the outline beside the document (one
 * setting for every file; closing it in the page changes the setting) and the formatting
 * toolbar over selections.
 */
import { useOutline } from "@/features/viewers/markdown/DocumentNav";
import { post } from "./bridge";
import type { PagePrefs } from "./protocol";

let applying = false;

export function applyPrefs(prefs: PagePrefs): void {
  document.documentElement.toggleAttribute("data-no-selection-toolbar", !prefs.selectionToolbar);
  if (useOutline.getState().open === prefs.outline) return;
  applying = true;
  try {
    useOutline.setState({ open: prefs.outline });
  } finally {
    applying = false;
  }
}

/** Reports the reader opening or closing the outline in the page. */
export function watchOutline(): () => void {
  return useOutline.subscribe((s, prev) => {
    if (!applying && s.open !== prev.open) post({ type: "outline", open: s.open });
  });
}
