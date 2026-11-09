/**
 * The workbench services the core uses: opening files (the IDE's editors) and
 * context-menu groups (gathered here, shown as IDE menus by `src/host/editorHost.ts`).
 */
import { post } from "../../host/bridge";
import type { MenuItem } from "../ui/Menu";

export interface EditorInput {
  kind: string;
  key: string;
  path?: string;
  data?: unknown;
}

export interface OpenOptions {
  /** Select / reveal this position (0-based line, UTF-16 col). */
  selection?: { line: number; col: number; endLine?: number; endCol?: number };
  preview?: boolean;
  side?: boolean;
  pane?: string;
  focus?: boolean;
  asText?: boolean;
}

export interface EditorService {
  openFile(path: string, options?: OpenOptions): Promise<void>;
}

const editorService: EditorService = {
  async openFile(path, options) {
    post({ type: "openFile", path, line: options?.selection?.line ?? null, col: options?.selection?.col ?? null });
  },
};

export function maybeEditorService(): EditorService | null {
  return editorService;
}

export type ContextMenuId = "editorTop" | (string & {});

export interface MenuGroupContext {
  paths: string[];
  line?: number;
}

export interface MenuGroupContribution {
  id: string;
  menus: ContextMenuId[];
  /** Lower first. */
  order?: number;
  items(menu: ContextMenuId, ctx: MenuGroupContext): MenuItem[];
}

const menuGroups = new Map<string, MenuGroupContribution>();

export function registerMenuGroup(group: MenuGroupContribution): () => void {
  menuGroups.set(group.id, group);
  return () => {
    if (menuGroups.get(group.id) === group) menuGroups.delete(group.id);
  };
}

/** Contributed items for `menu`, each non-empty group followed by a separator. */
export function menuGroupItems(menu: ContextMenuId, ctx: MenuGroupContext): MenuItem[] {
  const groups = [...menuGroups.values()]
    .filter((g) => g.menus.includes(menu))
    .sort((a, b) => (a.order ?? 0) - (b.order ?? 0) || a.id.localeCompare(b.id));
  const out: MenuItem[] = [];
  for (const g of groups) {
    let items: MenuItem[] = [];
    try {
      items = g.items(menu, ctx);
    } catch (e) {
      console.error(`menu group ${g.id} failed`, e);
    }
    if (items.length) out.push(...items, { kind: "separator" });
  }
  return out;
}
