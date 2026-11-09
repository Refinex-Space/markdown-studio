/**
 * The per-editor font zoom of Luma's `editor/core/cm/prefs` (the preview scales with it).
 * The IDE owns the editor font; this page only follows it.
 */
import { Prec, StateEffect, StateField } from "@codemirror/state";
import { EditorView } from "@codemirror/view";

export const setFontZoom = StateEffect.define<number>();

export const fontZoom = StateField.define<number>({
  create: () => 0,
  update: (v, tr) => tr.effects.reduce((acc, e) => (e.is(setFontZoom) ? e.value : acc), v),
  provide: (f) =>
    Prec.highest(
      EditorView.editorAttributes.compute([f], (s) => {
        const z = s.field(f);
        const attrs: Record<string, string> = {};
        if (z)
          attrs.style = `--luma-editor-font-size: calc(var(--luma-editor-base-size, var(--fs-code)) + ${z}px); --luma-editor-line-height: calc(var(--luma-editor-base-line, var(--lh-code)) + ${Math.round(z * 1.6)}px)`;
        return attrs;
      }),
    ),
});
