/**
 * The page: the core's Markdown editor wrapper (Live Preview, preview, outline, back-to-top) around
 * the page's CodeMirror editor. In the IDE's Split layout the IDE's own editor is the source
 * side, so this page shows the wrapper's split layout with its editor column hidden: the preview
 * follows the IDE editor's scrolling.
 */
import { useLayoutEffect, useRef } from "react";
import clsx from "clsx";
import { MarkdownEditor } from "@/features/viewers/markdown/MarkdownEditor";
import { TooltipHost } from "@/ui/Tooltip";
import { useSession } from "../host/session";
import "./host.css";

function EditorMount() {
  const editor = useSession((s) => s.editor);
  const ref = useRef<HTMLDivElement>(null);
  useLayoutEffect(() => {
    const host = ref.current;
    const dom = editor?.view.dom.parentElement;
    if (!host || !dom) return;
    host.append(dom);
    editor.view.requestMeasure();
    return () => dom.remove();
  }, [editor]);
  return <div ref={ref} className="host-editor-mount" />;
}

export function App() {
  const path = useSession((s) => s.path);
  const mode = useSession((s) => s.mode);
  if (!path) return null;
  return (
    <div className={clsx("host", `host-mode-${mode}`)}>
      <MarkdownEditor path={path} tabId="markdown-studio" actionsInTabs>
        <EditorMount />
      </MarkdownEditor>
      <TooltipHost />
    </div>
  );
}
