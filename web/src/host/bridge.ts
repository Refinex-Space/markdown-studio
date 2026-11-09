/**
 * The page's end of the IDE channel. The IDE serves `index.html` with `window.__studioPost`
 * defined (a JBCefJSQuery call) and delivers its messages by calling `window.__studioReceive`.
 * Outside the IDE (vite dev, tests) messages go nowhere unless a test installs a transport.
 */
import type { IdeMessage, PageMessage } from "./protocol";

declare global {
  interface Window {
    __studioPost?: (json: string) => void;
    __studioReceive?: (message: IdeMessage) => void;
  }
}

type Listener = (message: IdeMessage) => void;

const listeners = new Set<Listener>();
let transport: ((message: PageMessage) => void) | null = null;

export function post(message: PageMessage): void {
  if (transport) transport(message);
  else window.__studioPost?.(JSON.stringify(message));
}

export function onIdeMessage(listener: Listener): () => void {
  listeners.add(listener);
  return () => void listeners.delete(listener);
}

export function receive(message: IdeMessage): void {
  for (const l of listeners) {
    try {
      l(message);
    } catch (e) {
      console.error(`handling ${message.type} failed`, e);
    }
  }
}

/** Tests: capture outgoing messages instead of posting them to the IDE. */
export function setTransport(t: ((message: PageMessage) => void) | null): void {
  transport = t;
}

export function isInIde(): boolean {
  return typeof window.__studioPost === "function";
}

let nextRequest = 1;
const pending = new Map<number, { resolve: (v: unknown) => void; reject: (e: Error) => void }>();

export function request<T>(method: "readImage", path: string): Promise<T> {
  const id = nextRequest++;
  return new Promise<T>((resolve, reject) => {
    pending.set(id, { resolve: resolve as (v: unknown) => void, reject });
    post({ type: "request", id, method, path });
  });
}

onIdeMessage((m) => {
  if (m.type !== "response") return;
  const p = pending.get(m.id);
  if (!p) return;
  pending.delete(m.id);
  if (m.ok) p.resolve(m.data);
  else p.reject(new Error(m.error ?? "request failed"));
});

if (typeof window !== "undefined") window.__studioReceive = receive;
