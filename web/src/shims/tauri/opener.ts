/** `@tauri-apps/plugin-opener` for the vendored sources: web links open in the IDE's configured browser. */
import { post } from "../../host/bridge";

export async function openUrl(url: string): Promise<void> {
  post({ type: "openExternal", url });
}
