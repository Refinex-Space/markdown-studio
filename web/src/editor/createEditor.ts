/**
 * The page's CodeMirror editor: the Markdown language and the core's editor look, the document
 * synced with the IDE, and every registered editor contribution (Live Preview among them).
 * No undo history here: undo and redo are the IDE's, over its document.
 */
import { closeBrackets, closeBracketsKeymap } from "@codemirror/autocomplete";
import { indentWithTab } from "@codemirror/commands";
import { indentOnInput, syntaxHighlighting } from "@codemirror/language";
import { searchKeymap } from "@codemirror/search";
import { Compartment, EditorSelection, EditorState, type Extension } from "@codemirror/state";
import { drawSelection, dropCursor, EditorView, highlightSpecialChars, keymap } from "@codemirror/view";
import {
  _addEditor,
  _removeEditor,
  _setActiveEditor,
  contributionExtensions,
  type EditorDocContext,
  type TextEditorHandle,
  useEditorRegistry,
} from "@/editor/api";
import { editorBaseKeymap } from "@/editor/core/cm/baseKeymap";
import { fontZoom } from "@/editor/core/cm/prefs";
import { lumaHighlightStyle } from "@/editor/core/cm/highlight";
import { markdownSupport } from "@/editor/core/cm/markdownLang";
import { lumaTheme } from "@/editor/core/cm/theme";
import { visibleGoalColumns } from "@/editor/core/cm/verticalMotion";
import { tx } from "@/platform/i18n";
import { documentSync } from "./documentSync";
import { blockSelectAll, selectionStack } from "./editingCommands";
import { findPanel } from "./findPanel";

export interface EditorOptions {
  path: string;
  text: string;
  version: number;
  readOnly: boolean;
  selection?: { anchor: number; head: number };
  /** Extra extensions of the host (focus, selection and context-menu reporting). */
  host?: Extension;
}

/** Editing behaviour the IDE's editor settings decide. */
export interface EditingPrefs {
  caretBlink: number;
  pairBrackets: boolean;
}

export interface PageEditor {
  readonly view: EditorView;
  readonly handle: TextEditorHandle;
  setReadOnly(readOnly: boolean): void;
  setEditingPrefs(prefs: EditingPrefs): void;
  /** Replaces the document and its sync state (the IDE resent its document). */
  reset(text: string, version: number): void;
  destroy(): void;
}

const readOnlyExtension = (readOnly: boolean): Extension =>
  readOnly ? [EditorState.readOnly.of(true), EditorView.editable.of(false)] : [];

function clampSelection(sel: { anchor: number; head: number } | undefined, length: number) {
  if (!sel) return undefined;
  const c = (n: number) => Math.max(0, Math.min(length, n));
  return EditorSelection.single(c(sel.anchor), c(sel.head));
}

export function createEditor(parent: HTMLElement, o: EditorOptions): PageEditor {
  const ctx: EditorDocContext = { doc: 1, path: o.path, language: "markdown", uiMode: "Markdown", readOnly: o.readOnly };
  const readOnly = new Compartment();
  const contributions = new Compartment();
  const editing = new Compartment();
  let editingPrefs: EditingPrefs = { caretBlink: 1000, pairBrackets: true };
  const editingExtension = (p: EditingPrefs): Extension => [
    drawSelection({ cursorBlinkRate: p.caretBlink }),
    p.pairBrackets ? [closeBrackets(), keymap.of(closeBracketsKeymap)] : [],
  ];

  const extensions = (version: number): Extension => [
    documentSync(version),
    readOnly.of(readOnlyExtension(ctx.readOnly)),
    markdownSupport(),
    lumaTheme,
    syntaxHighlighting(lumaHighlightStyle),
    highlightSpecialChars(),
    editing.of(editingExtension(editingPrefs)),
    dropCursor(),
    EditorState.allowMultipleSelections.of(true),
    visibleGoalColumns,
    selectionStack,
    indentOnInput(),
    EditorView.lineWrapping,
    fontZoom,
    findPanel(),
    EditorView.contentAttributes.of({
      spellcheck: "false",
      autocorrect: "off",
      autocapitalize: "off",
      "aria-label": tx`Editor for ${o.path}`,
    }),
    // The core's editing keys: CodeMirror's defaults minus the ones IDEA gives to its own actions.
    keymap.of([...editorBaseKeymap, ...searchKeymap, indentWithTab]),
    blockSelectAll,
    contributions.of(contributionExtensions(ctx)),
    o.host ?? [],
  ];

  const state = (text: string, version: number, selection?: { anchor: number; head: number }) =>
    EditorState.create({ doc: text, selection: clampSelection(selection, text.length), extensions: extensions(version) });

  const view = new EditorView({ state: state(o.text, o.version, o.selection), parent });
  const handle: TextEditorHandle = { view, ctx };
  _addEditor(handle);
  _setActiveEditor(handle);

  // Contributions registered later (or replaced) apply to the open editor.
  const unsubscribe = useEditorRegistry.subscribe((s, prev) => {
    if (s.contributions !== prev.contributions)
      view.dispatch({ effects: contributions.reconfigure(contributionExtensions(ctx)) });
  });

  return {
    view,
    handle,
    setEditingPrefs(prefs) {
      if (prefs.caretBlink === editingPrefs.caretBlink && prefs.pairBrackets === editingPrefs.pairBrackets) return;
      editingPrefs = prefs;
      view.dispatch({ effects: editing.reconfigure(editingExtension(prefs)) });
    },
    setReadOnly(value) {
      if (ctx.readOnly === value) return;
      ctx.readOnly = value;
      view.dispatch({ effects: readOnly.reconfigure(readOnlyExtension(value)) });
    },
    reset(text, version) {
      const sel = view.state.selection.main;
      view.setState(state(text, version, { anchor: sel.anchor, head: sel.head }));
    },
    destroy() {
      unsubscribe();
      _removeEditor(handle);
      view.destroy();
    },
  };
}
