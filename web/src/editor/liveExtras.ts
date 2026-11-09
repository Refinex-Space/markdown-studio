/**
 * Live Preview additions of the host: character references (`&copy;`, `&#8594;`) show the
 * character, and a backslash hard break hides its backslash, unless the caret is at them (as
 * Live Preview treats every other mark).
 */
import { syntaxTree } from "@codemirror/language";
import { RangeSetBuilder, type EditorState } from "@codemirror/state";
import { Decoration, EditorView, ViewPlugin, WidgetType, type DecorationSet, type ViewUpdate } from "@codemirror/view";
import { isLive, touches } from "@/features/viewers/markdown/livePreview/state";

class CharacterWidget extends WidgetType {
  constructor(readonly text: string) {
    super();
  }
  eq(other: CharacterWidget) {
    return other.text === this.text;
  }
  toDOM() {
    const span = document.createElement("span");
    span.className = "cm-md-entity";
    span.textContent = this.text;
    return span;
  }
  ignoreEvent() {
    return false;
  }
}

const decoder = typeof document !== "undefined" ? document.createElement("textarea") : null;

/**
 * The character an HTML character reference stands for, or null for an unknown name (CommonMark:
 * only whole, known references count; HTML would decode `&notanentity;` as `¬anentity;`).
 */
export function decodeEntity(source: string): string | null {
  if (!decoder || !/^&(?:#\d{1,7}|#[xX][0-9a-fA-F]{1,6}|[A-Za-z][A-Za-z0-9]{1,31});$/.test(source)) return null;
  decoder.innerHTML = source;
  const text = decoder.value;
  if (!text || text === source) return null;
  return source.startsWith("&#") || !text.endsWith(";") ? text : null;
}

const hideMark = Decoration.replace({});

function build(view: EditorView): DecorationSet {
  const { state } = view;
  const out = new RangeSetBuilder<Decoration>();
  if (!isLive(state)) return out.finish();
  for (const { from, to } of view.visibleRanges) {
    syntaxTree(state).iterate({
      from,
      to,
      enter: (node) => {
        if (node.name === "FencedCode" || node.name === "CodeBlock" || node.name === "InlineCode") return false;
        if (node.name === "Entity" && !touches(state, node.from, node.to)) {
          const text = decodeEntity(state.sliceDoc(node.from, node.to));
          if (text) out.add(node.from, node.to, Decoration.replace({ widget: new CharacterWidget(text) }));
        } else if (node.name === "HardBreak" && backslashBreak(state, node.from) && !touches(state, node.from, node.to)) {
          out.add(node.from, node.from + 1, hideMark);
        }
        return undefined;
      },
    });
  }
  return out.finish();
}

const backslashBreak = (state: EditorState, at: number) => state.sliceDoc(at, at + 1) === "\\";

export const liveExtras = ViewPlugin.fromClass(
  class {
    decorations: DecorationSet;
    constructor(view: EditorView) {
      this.decorations = build(view);
    }
    update(u: ViewUpdate) {
      if (u.docChanged || u.viewportChanged || u.selectionSet || u.focusChanged || u.transactions.some((t) => t.effects.length))
        this.decorations = build(u.view);
    }
  },
  { decorations: (v) => v.decorations },
);
