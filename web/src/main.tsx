import { createRoot } from "react-dom/client";
import { registerMarkdownLiveSource } from "@/features/viewers/markdown/live";
import { registerLivePreview } from "@/features/viewers/markdown/livePreview";
import { App } from "./app/App";
import { registerEditorContribution } from "@/editor/api";
import { registerEditingCommands } from "./editor/editingCommands";
import { liveExtras } from "./editor/liveExtras";
import { installClipboard } from "./host/clipboard";
import { watchRemoteImages } from "./host/remoteImages";
import { startSession } from "./host/session";
import "./app/base.css";

// The Markdown features, registered as the core's viewers module does.
registerMarkdownLiveSource();
registerLivePreview();
registerEditingCommands();
registerEditorContribution({ id: "studio.liveExtras", extensions: () => liveExtras });
installClipboard();
watchRemoteImages();

createRoot(document.getElementById("root")!).render(<App />);
startSession();
