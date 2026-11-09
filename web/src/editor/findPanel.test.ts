import { describe, expect, it } from "vitest";
import { EditorSelection, EditorState } from "@codemirror/state";
import { SearchQuery } from "@codemirror/search";
import { matchPosition } from "./findPanel";

describe("the find bar's count", () => {
  const state = (doc: string, from = 0, to = from) => EditorState.create({ doc, selection: EditorSelection.single(from, to) });

  it("counts matches and finds the selected one", () => {
    const doc = "one two one three one";
    expect(matchPosition(state(doc, 8, 11), new SearchQuery({ search: "one" }))).toEqual({ current: 2, total: 3 });
    expect(matchPosition(state(doc), new SearchQuery({ search: "One", caseSensitive: true }))).toEqual({ current: 0, total: 0 });
    expect(matchPosition(state(doc), new SearchQuery({ search: "t\\w+", regexp: true }))).toEqual({ current: 0, total: 2 });
  });

  it("counts nothing for an empty or invalid query", () => {
    expect(matchPosition(state("abc"), new SearchQuery({ search: "" }))).toEqual({ current: 0, total: 0 });
    expect(matchPosition(state("abc"), new SearchQuery({ search: "(", regexp: true }))).toEqual({ current: 0, total: 0 });
  });
});
