/**
 * The backend calls the core makes, answered by the IDE. The IDE also plays
 * Tauri's part for the opener (`isTauri()` → web links go to `@tauri-apps/plugin-opener`, which
 * `src/shims/tauri/opener.ts` hands to the IDE's browser launcher).
 */
import { request } from "../../host/bridge";

export type { FileProbe } from "../bindings";

interface IdeError {
  kind: "io";
  message: string;
}

type Result<T> = { status: "ok"; data: T } | { status: "error"; error: IdeError };

export class IpcError extends Error {
  readonly kind: IdeError["kind"];
  constructor(error: IdeError) {
    super(error.message);
    this.name = "IpcError";
    this.kind = error.kind;
  }
}

export async function call<T>(result: Promise<Result<T>>): Promise<T> {
  const r = await result;
  if (r.status === "error") throw new IpcError(r.error);
  return r.data;
}

const settle = <T>(p: Promise<T>): Promise<Result<T>> =>
  p.then(
    (data) => ({ status: "ok", data }),
    (e: unknown) => ({ status: "error", error: { kind: "io", message: e instanceof Error ? e.message : String(e) } }),
  );

export const commands = {
  /** A local image as a data URL (the page cannot read files). */
  fsReadImage: (path: string) => settle(request<string>("readImage", path)),
};

export function isTauri(): boolean {
  return true;
}
