/**
 * One open Markdown file: created on the IDE's `init`, it owns the editor and applies the IDE's
 * messages (document updates, layout, theme, locale, shortcuts, actions run from IDE menus and
 * keys). The core's components read their usual stores, which this session fills.
 */
import { setContext } from "@/platform/context";
import { executeAction } from "@/platform/actions";
import { setShortcuts } from "@/platform/keymap";
import { useLiveMarkdown } from "@/features/viewers/markdown/live";
import { useMarkdownModes } from "@/features/viewers/markdown/modes";
import { create } from "zustand";
import { createEditor, type PageEditor } from "../editor/createEditor";
import { receive } from "../editor/documentSync";
import { onIdeMessage, post } from "./bridge";
import { editorHost } from "./editorHost";
import type { IdeMessage, PageMode } from "./protocol";
import { setPageLocale } from "./locale";
import { applyPrefs, watchOutline } from "./prefs";
import { setRemoteImagesAllowed } from "./remoteImages";
import { applyTheme } from "./theme";

export interface SessionState {
  path: string | null;
  mode: PageMode;
  editor: PageEditor | null;
}

export const useSession = create<SessionState>(() => ({ path: null, mode: "live", editor: null }));

function setMode(path: string, mode: PageMode): void {
  useSession.setState({ mode });
  // The wrapper's layouts: Split is the IDE's Source editor beside this page's preview (see App).
  useMarkdownModes.setState((s) => ({ byPath: { ...s.byPath, [path]: mode }, fallback: mode }));
}

function scrollPreview(path: string, topLine: number): void {
  useLiveMarkdown.setState((s) => {
    const prev = s[path] ?? { text: "", topLine: 0, scrollSeq: 0, fontZoom: 0 };
    return { [path]: { ...prev, topLine, scrollSeq: prev.scrollSeq + 1 } };
  });
}

async function init(m: Extract<IdeMessage, { type: "init" }>): Promise<void> {
  useSession.getState().editor?.destroy();
  document.title = m.path.slice(m.path.lastIndexOf("/") + 1);
  applyTheme(m.theme);
  setShortcuts(m.shortcuts);
  setRemoteImagesAllowed(m.remoteImages);
  applyPrefs(m.prefs);
  // The Markdown actions apply to the focused Markdown editor. This page has just that one, and
  // the IDE decides when its actions apply (an IDE menu over the page takes the focus meanwhile).
  setContext("editorTextFocus", true);
  setContext("textEditorActive", true);
  setContext("editorLangId", "markdown");
  await setPageLocale(m.locale);
  const parent = document.createElement("div");
  parent.className = "host-editor";
  const editor = createEditor(parent, {
    path: m.path,
    text: m.text,
    version: m.version,
    readOnly: m.readOnly,
    selection: m.selection,
    host: editorHost(m.path),
  });
  setMode(m.path, m.mode);
  editor.setEditingPrefs(m.prefs);
  useSession.setState({ path: m.path, editor });
  reportRendered(parent);
}

/** Tells the IDE once the document is on screen: its loading skeleton then fades out. */
function reportRendered(parent: HTMLElement): void {
  const painted = () => requestAnimationFrame(() => requestAnimationFrame(() => post({ type: "rendered" })));
  const attached = () => (parent.isConnected ? painted() : requestAnimationFrame(attached));
  requestAnimationFrame(attached);
}

export function startSession(): () => void {
  const off = onIdeMessage((m) => {
    const { editor, path } = useSession.getState();
    switch (m.type) {
      case "init":
        void init(m);
        return;
      case "theme":
        applyTheme(m.theme);
        return;
      case "locale":
        void setPageLocale(m.locale);
        return;
      case "shortcuts":
        setShortcuts(m.shortcuts);
        return;
      case "remoteImages":
        setRemoteImagesAllowed(m.allowed);
        return;
      case "prefs":
        applyPrefs(m.prefs);
        useSession.getState().editor?.setEditingPrefs(m.prefs);
        return;
    }
    if (!editor || !path) return;
    const { view } = editor;
    switch (m.type) {
      case "updates":
        if (!receive(view, m.version, m.updates)) post({ type: "resync" });
        break;
      case "reset":
        editor.reset(m.text, m.version);
        break;
      case "mode":
        setMode(path, m.mode);
        if (m.mode === "live") requestAnimationFrame(() => view.requestMeasure());
        break;
      case "readOnly":
        editor.setReadOnly(m.readOnly);
        break;
      case "scroll":
        scrollPreview(path, m.topLine);
        break;
      case "select": {
        const len = view.state.doc.length;
        const c = (n: number) => Math.max(0, Math.min(len, n));
        view.dispatch({ selection: { anchor: c(m.anchor), head: c(m.head) }, scrollIntoView: true });
        if (m.focus) view.focus();
        break;
      }
      case "focus":
        view.focus();
        break;
      case "action":
        view.focus();
        void executeAction(m.id, m.arg);
        break;
    }
  });
  // The IDE focuses the page (tab switch, layout switch): typing goes on in the editor.
  const onWindowFocus = () => {
    const { editor, mode } = useSession.getState();
    if (editor && mode === "live" && document.activeElement === document.body) editor.view.focus();
  };
  window.addEventListener("focus", onWindowFocus);
  const offOutline = watchOutline();
  post({ type: "ready" });
  return () => {
    off();
    offOutline();
    window.removeEventListener("focus", onWindowFocus);
  };
}
