/**
 * The editor registry of the core's `editor/api`, for the one CodeMirror editor of this page.
 * Features register per-editor extensions; the page's editor applies them
 * (`src/editor/createEditor.ts`) and registers itself here.
 */
import { Annotation, Facet, type Extension } from "@codemirror/state";
import type { EditorView } from "@codemirror/view";
import { create } from "zustand";

export interface EditorDocContext {
  doc: number;
  path: string;
  /** Backend language id, e.g. `markdown`. */
  language: string;
  uiMode: string | null;
  readOnly: boolean;
  virtual?: boolean;
}

export interface EditorContribution {
  id: string;
  /** Lower runs first. Default 100. */
  order?: number;
  extensions: (ctx: EditorDocContext) => Extension | Extension[];
  embedded?: boolean;
}

export interface TextEditorHandle {
  view: EditorView;
  ctx: EditorDocContext;
}

interface EditorRegistry {
  contributions: Map<string, EditorContribution>;
  active: TextEditorHandle | null;
  editors: Set<TextEditorHandle>;
}

export const useEditorRegistry = create<EditorRegistry>(() => ({
  contributions: new Map(),
  active: null,
  editors: new Set(),
}));

export function registerEditorContribution(c: EditorContribution): () => void {
  useEditorRegistry.setState((s) => ({ contributions: new Map(s.contributions).set(c.id, c) }));
  return () =>
    useEditorRegistry.setState((s) => {
      const contributions = new Map(s.contributions);
      contributions.delete(c.id);
      return { contributions };
    });
}

export function editorContributions(): EditorContribution[] {
  return [...useEditorRegistry.getState().contributions.values()].sort((a, b) => (a.order ?? 100) - (b.order ?? 100));
}

export function contributionExtensions(ctx: EditorDocContext): Extension[] {
  const out: Extension[] = [];
  for (const c of editorContributions()) {
    try {
      out.push(c.extensions(ctx));
    } catch (e) {
      console.error(`editor contribution ${c.id} failed`, e);
    }
  }
  return out;
}

export function activeEditor(): TextEditorHandle | null {
  return useEditorRegistry.getState().active;
}

export function editorsFor(path: string): TextEditorHandle[] {
  return [...useEditorRegistry.getState().editors].filter((e) => e.ctx.path === path);
}

export function _setActiveEditor(handle: TextEditorHandle | null) {
  useEditorRegistry.setState({ active: handle });
}

export function _addEditor(handle: TextEditorHandle) {
  useEditorRegistry.setState((s) => ({ editors: new Set(s.editors).add(handle), active: s.active ?? handle }));
}

export function _removeEditor(handle: TextEditorHandle) {
  useEditorRegistry.setState((s) => {
    const editors = new Set(s.editors);
    editors.delete(handle);
    return { editors, active: s.active === handle ? null : s.active };
  });
}

/** Set on transactions that apply the IDE document's changes (not typed in this view). */
export const remoteChange = Annotation.define<boolean>();

/** The core hides the minimap of documents that read as text; this editor has none. */
export const minimapHidden = Facet.define<boolean, boolean>({ combine: (values) => values.some(Boolean) });
