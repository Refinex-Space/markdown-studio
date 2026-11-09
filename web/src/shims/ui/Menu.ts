/**
 * The page's context menus, shown as IDE popup menus: the items are resolved here (actions, labels
 * through the catalog) and sent to the IDE; the IDE calls back with the chosen item.
 */
import { executeAction, getAction, isActionEnabled } from "@/platform/actions";
import { tx } from "@/platform/i18n";
import { shortcutLabel } from "@/platform/keymap";
import { onIdeMessage, post } from "../../host/bridge";
import type { NativeMenuItem } from "../../host/protocol";

export type MenuItem =
  | { kind: "separator" }
  | { kind: "header"; label: string }
  | {
      kind: "action";
      action: string;
      arg?: unknown;
      label?: string;
      icon?: string;
      disabled?: boolean;
    }
  | {
      kind?: "item";
      id?: string;
      label: string;
      verbatim?: boolean;
      icon?: string;
      shortcut?: string;
      disabled?: boolean;
      checked?: boolean;
      danger?: boolean;
      run?: () => void;
      submenu?: MenuItem[];
    };

let nextMenu = 1;
/** Item callbacks of the menu the IDE shows last (one context menu is open at a time). */
const open = new Map<number, Map<string, () => void>>();

/** Resolves `items` into the wire form, collecting each runnable item's callback in `runs`. */
export function nativeMenuItems(items: MenuItem[], runs: Map<string, () => void>, prefix = ""): NativeMenuItem[] {
  const out: NativeMenuItem[] = [];
  items.forEach((item, i) => {
    const id = `${prefix}${i}`;
    if (item.kind === "separator") {
      if (out.length && out[out.length - 1]!.kind !== "separator") out.push({ id, kind: "separator" });
      return;
    }
    if (item.kind === "header") {
      out.push({ id, kind: "header", label: tx(item.label) });
      return;
    }
    if (item.kind === "action") {
      const a = getAction(item.action);
      if (!a) return;
      runs.set(id, () => void executeAction(item.action, item.arg));
      out.push({
        id,
        kind: "item",
        label: tx(item.label) ?? a.title,
        icon: item.icon ?? a.icon,
        shortcut: shortcutLabel(item.action),
        disabled: !!item.disabled || !isActionEnabled(a),
        action: item.action,
      });
      return;
    }
    if (item.run) runs.set(id, item.run);
    out.push({
      id,
      kind: "item",
      label: item.verbatim ? item.label : tx(item.label),
      icon: item.icon,
      shortcut: item.shortcut,
      disabled: item.disabled,
      checked: item.checked,
      danger: item.danger,
      children: item.submenu ? nativeMenuItems(item.submenu, runs, `${id}.`) : undefined,
    });
  });
  while (out.length && out[out.length - 1]!.kind === "separator") out.pop();
  return out;
}

/** Shows `items` as an IDE menu at a point of the page; `editor` adds the IDE's Cut / Copy / Paste. */
export function showNativeMenu(x: number, y: number, items: MenuItem[], editor = false): void {
  const runs = new Map<string, () => void>();
  const native = nativeMenuItems(items, runs);
  if (!native.length && !editor) return;
  const menuId = nextMenu++;
  open.clear();
  open.set(menuId, runs);
  post({ type: "contextMenu", menuId, x: Math.round(x), y: Math.round(y), items: native, editor });
}

export function showContextMenu(
  at: { clientX: number; clientY: number } | { x: number; y: number },
  items: MenuItem[],
): void {
  const x = "clientX" in at ? at.clientX : at.x;
  const y = "clientY" in at ? at.clientY : at.y;
  showNativeMenu(x, y, items);
}

export function hideContextMenu(): void {}

onIdeMessage((m) => {
  if (m.type !== "menuInvoke") return;
  const run = open.get(m.menuId)?.get(m.itemId);
  open.delete(m.menuId);
  run?.();
});
