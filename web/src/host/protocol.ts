/**
 * Messages between this page and the IDE (`com.refinex.markdownstudio.bridge` on the Kotlin
 * side). Both directions are JSON objects tagged by `type`; the Kotlin classes mirror these.
 *
 * Document sync follows @codemirror/collab: the IDE document is the authority, every change
 * has a version, the page pushes its unconfirmed changes against the version it has seen and
 * rebases them when the IDE answers with changes it had not seen yet.
 */
import type { ChangeSetJSON } from "./changeSet";

export type PageMode = "live" | "preview" | "split";

export interface ThemePayload {
  /** `light` or `dark`: picks the page's token set and Mermaid's theme. */
  scheme: "light" | "dark";
  /** CSS custom properties (`--bg-app` → `#1e1f22`) taken from the IDE's look and editor scheme. */
  tokens: Record<string, string>;
}

export interface PagePrefs {
  outline: boolean;
  selectionToolbar: boolean;
  /** A full caret blink cycle in ms (the IDE's setting); 0: no blinking. */
  caretBlink: number;
  /** The IDE's Smart Keys: insert pair brackets and quotes. */
  pairBrackets: boolean;
}

export interface WireUpdate {
  changes: ChangeSetJSON;
  clientID: string;
}

export interface NativeMenuItem {
  id: string;
  kind: "item" | "separator" | "header";
  label?: string;
  icon?: string;
  shortcut?: string;
  disabled?: boolean;
  checked?: boolean;
  danger?: boolean;
  /** For page action items: the action (its IDE counterpart supplies the shortcut shown). */
  action?: string;
  children?: NativeMenuItem[];
}

export type IdeMessage =
  | {
      type: "init";
      path: string;
      text: string;
      version: number;
      readOnly: boolean;
      mode: PageMode;
      locale: string;
      theme: ThemePayload;
      shortcuts: Record<string, string>;
      remoteImages: boolean;
      prefs: PagePrefs;
      selection?: { anchor: number; head: number };
    }
  | { type: "updates"; version: number; updates: WireUpdate[] }
  | { type: "reset"; text: string; version: number }
  | { type: "mode"; mode: PageMode }
  | { type: "theme"; theme: ThemePayload }
  | { type: "locale"; locale: string }
  | { type: "shortcuts"; shortcuts: Record<string, string> }
  | { type: "readOnly"; readOnly: boolean }
  | { type: "remoteImages"; allowed: boolean }
  | { type: "prefs"; prefs: PagePrefs }
  | { type: "scroll"; topLine: number }
  | { type: "select"; anchor: number; head: number; focus: boolean }
  | { type: "focus" }
  | { type: "action"; id: string; arg?: string }
  | { type: "menuInvoke"; menuId: number; itemId: string }
  | { type: "response"; id: number; ok: boolean; data?: unknown; error?: string };

export type PageMessage =
  | { type: "ready" }
  | { type: "rendered" }
  | { type: "resync" }
  | { type: "push"; version: number; updates: WireUpdate[]; userEvent: string | null }
  | { type: "selection"; anchor: number; head: number }
  | { type: "focus"; focused: boolean }
  | { type: "outline"; open: boolean }
  | { type: "openFile"; path: string; line: number | null; col: number | null }
  | { type: "openExternal"; url: string }
  | { type: "copyText"; text: string }
  | { type: "contextMenu"; menuId: number; x: number; y: number; items: NativeMenuItem[]; editor: boolean }
  | { type: "request"; id: number; method: "readImage"; path: string }
  | { type: "log"; level: "info" | "warn" | "error"; message: string };
