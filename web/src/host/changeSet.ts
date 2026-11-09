/** The JSON form of a CodeMirror `ChangeSet` (`ChangeSet.toJSON()`), as exchanged with the IDE. */
export type ChangeSetJSON = ReadonlyArray<number | readonly [number, ...string[]]>;
