/**
 * What the IDE needs to know about the page's editor: focus (Markdown actions apply while it
 * has it), the selection (the Source editor takes it over on a layout switch) and right-clicks
 * (the IDE shows the editor menu: its Cut / Copy / Paste, then the page's Format / Paragraph / Insert).
 */
import { EditorView, ViewPlugin } from "@codemirror/view";
import { menuGroupItems } from "@/workbench/contributions";
import { showNativeMenu } from "@/ui/Menu";
import { post } from "./bridge";

const SELECTION_DELAY = 120;

export function editorHost(path: string) {
  return [
    ViewPlugin.define((view) => {
      let timer: ReturnType<typeof setTimeout> | undefined;
      const report = () => {
        const { anchor, head } = view.state.selection.main;
        post({ type: "selection", anchor, head });
      };
      return {
        update(u) {
          if (u.focusChanged) post({ type: "focus", focused: view.hasFocus });
          if (!u.selectionSet && !u.docChanged) return;
          clearTimeout(timer);
          timer = setTimeout(report, SELECTION_DELAY);
        },
        destroy() {
          clearTimeout(timer);
        },
      };
    }),
    EditorView.domEventHandlers({
      contextmenu(e, view) {
        e.preventDefault();
        const line = view.state.doc.lineAt(view.state.selection.main.head).number - 1;
        showNativeMenu(e.clientX, e.clientY, menuGroupItems("editorTop", { paths: [path], line }), true);
        return true;
      },
    }),
  ];
}
