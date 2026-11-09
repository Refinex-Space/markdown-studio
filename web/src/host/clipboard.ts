/**
 * Text the page copies (code block and table copy buttons) goes through the IDE clipboard, so
 * it shows in Paste from History and works whatever the page's clipboard permissions are.
 */
import { isInIde, post } from "./bridge";

export function installClipboard(): void {
  if (!isInIde()) return;
  const writeText = (text: string) => {
    post({ type: "copyText", text });
    return Promise.resolve();
  };
  const clipboard = navigator.clipboard;
  if (clipboard) Object.defineProperty(clipboard, "writeText", { value: writeText, configurable: true });
  else Object.defineProperty(navigator, "clipboard", { value: { writeText }, configurable: true });
}
