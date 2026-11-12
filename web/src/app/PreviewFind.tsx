/**
 * The find bar over the preview (Preview and Split layouts), opened by the IDE's Find
 * (⌘F / Ctrl+F) like Live Preview's: see src/editor/previewFind.ts.
 */
import { useEffect, useRef } from "react";
import { onPreviewFind, PreviewFind as Find } from "../editor/previewFind";

export function PreviewFind() {
  const host = useRef<HTMLDivElement>(null);

  useEffect(() => {
    let find: Find | null = null;
    const close = () => {
      find?.destroy();
      find = null;
    };
    const open = () => {
      const root = host.current?.parentElement?.querySelector<HTMLElement>('[role="document"]');
      if (!host.current || !root) return;
      find ??= new Find(root, host.current, close);
      find.open();
    };
    // The IDE takes ⌘F for its Find action; in a plain browser (development) the page gets it.
    const keydown = (e: KeyboardEvent) => {
      if (e.defaultPrevented || e.altKey || e.shiftKey || !(e.metaKey || e.ctrlKey) || e.key.toLowerCase() !== "f") return;
      e.preventDefault();
      open();
    };
    window.addEventListener("keydown", keydown);
    const off = onPreviewFind(open);
    return () => {
      off();
      window.removeEventListener("keydown", keydown);
      close();
    };
  }, []);

  return <div ref={host} className="host-preview-find" />;
}
