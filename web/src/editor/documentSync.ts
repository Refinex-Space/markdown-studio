/**
 * Keeps this page's document in step with the IDE document (the authority) using
 * @codemirror/collab: local changes are pushed with the version they are based on; the IDE
 * answers every push with the updates since that version (ours included once accepted), and
 * changes made in the IDE (Source editor, undo, VCS, reformat) arrive the same way.
 */
import {
  collab,
  getClientID,
  getSyncedVersion,
  receiveUpdates,
  sendableUpdates,
  type Update,
} from "@codemirror/collab";
import { ChangeSet, Transaction, type Extension } from "@codemirror/state";
import { EditorView, ViewPlugin, type ViewUpdate } from "@codemirror/view";
import { remoteChange } from "@/editor/api";
import { post } from "../host/bridge";
import type { WireUpdate } from "../host/protocol";

/** One push at a time: the next waits for the IDE's answer to the previous one. */
const inflight = new WeakMap<EditorView, boolean>();
const scheduled = new WeakSet<EditorView>();

function userEventOf(update: ViewUpdate): string | null {
  for (let i = update.transactions.length - 1; i >= 0; i--) {
    const e = update.transactions[i]!.annotation(Transaction.userEvent);
    if (e) return e;
  }
  return null;
}

const lastUserEvent = new WeakMap<EditorView, string | null>();

export function push(view: EditorView): void {
  if (inflight.get(view)) return;
  const updates = sendableUpdates(view.state);
  if (!updates.length) return;
  inflight.set(view, true);
  post({
    type: "push",
    version: getSyncedVersion(view.state),
    updates: updates.map((u) => ({ changes: u.changes.toJSON() as WireUpdate["changes"], clientID: u.clientID })),
    userEvent: lastUserEvent.get(view) ?? null,
  });
}

function schedulePush(view: EditorView): void {
  if (scheduled.has(view)) return;
  scheduled.add(view);
  queueMicrotask(() => {
    scheduled.delete(view);
    push(view);
  });
}

const pusher = ViewPlugin.define((view) => ({
  update(u: ViewUpdate) {
    if (!u.docChanged || u.transactions.every((tr) => tr.annotation(remoteChange))) return;
    lastUserEvent.set(view, userEventOf(u));
    schedulePush(view);
  },
}));

export function documentSync(startVersion: number): Extension {
  return [collab({ startVersion }), pusher];
}

/**
 * Applies the IDE's updates (an empty list answers a push the IDE could not take: the updates
 * it had sent before are rebased over by now, so the push is retried). Returns false when the
 * updates do not continue this document's version (the page then asks for a reset).
 */
export function receive(view: EditorView, version: number, wire: readonly WireUpdate[]): boolean {
  const synced = getSyncedVersion(view.state);
  const start = version - wire.length;
  if (start > synced) return false;
  // Updates this page already has (a resent answer) are skipped.
  const fresh = wire.slice(synced - start);
  if (fresh.length) {
    const updates: Update[] = fresh.map((u) => ({ changes: ChangeSet.fromJSON(u.changes), clientID: u.clientID }));
    const own = getClientID(view.state);
    const remote = updates.some((u) => u.clientID !== own);
    view.dispatch(receiveUpdates(view.state, updates), {
      annotations: remote ? [remoteChange.of(true), Transaction.addToHistory.of(false)] : [],
    });
  }
  inflight.set(view, false);
  schedulePush(view);
  return true;
}

export { getSyncedVersion };
