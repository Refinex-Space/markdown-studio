/**
 * Every Markdown action and editing command of the page has its IDE action in plugin.xml (and no
 * IDE action points at a missing one): a command added to the page without its IDE action fails here.
 */
import fs from "node:fs";
import path from "node:path";
import { describe, expect, it } from "vitest";
import { allActions } from "@/platform/actions";
import { registerMarkdownActions } from "@/features/viewers/markdown/livePreview/actions";
import { EDITING_COMMANDS } from "./editor/editingCommands";

const pluginXml = fs.readFileSync(path.resolve(import.meta.dirname, "../../src/main/resources/META-INF/plugin.xml"), "utf8");
const ids = (cls: string) =>
  [...pluginXml.matchAll(new RegExp(`<action id="([^"]+)" class="[^"]*\\.${cls}"`, "g"))].map((m) => m[1]!);
const lowerFirst = (s: string) => s.charAt(0).toLowerCase() + s.slice(1);

describe("IDE actions for the page's commands", () => {
  it("covers every Markdown action of the page", () => {
    const off = registerMarkdownActions();
    const page = allActions().map((a) => a.id).filter((id) => id.startsWith("markdown.")).sort();
    off();
    const ide = ids("MarkdownCommandAction").map((id) => `markdown.${lowerFirst(id.replace("MarkdownStudio.", ""))}`).sort();
    expect(ide).toEqual(page);
  });

  it("covers every page editing command", () => {
    const ide = ids("PageEditorAction").map((id) => lowerFirst(id.replace("MarkdownStudio.Page.", ""))).sort();
    expect(ide).toEqual(Object.keys(EDITING_COMMANDS).sort());
  });
});
