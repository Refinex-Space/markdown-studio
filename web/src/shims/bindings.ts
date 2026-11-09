/** The few backend types the core names. */
export interface Markup {
  kind: "markdown" | "plaintext";
  value: string;
}

export interface Range {
  start: { line: number; character: number };
  end: { line: number; character: number };
}

export interface FileProbe {
  size: number;
  binary: boolean;
  tooLarge: boolean;
}
