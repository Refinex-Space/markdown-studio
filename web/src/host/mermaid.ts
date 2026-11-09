/**
 * Mermaid as the page uses it (`import("mermaid")` in the core's `mermaid.ts` resolves here): the core's
 * neutral theme, plus chart colours taken from the IDE's palette (pie slices, Gantt bars, mind
 * map branches, timelines), which the neutral greys alone leave indistinguishable.
 */
import mermaid from "mermaid";

type Config = Parameters<typeof mermaid.initialize>[0];

const HEX = /^#([0-9a-f]{6})$/i;

function rgb(hex: string): [number, number, number] | null {
  const m = HEX.exec(hex.trim());
  if (!m) return null;
  const n = parseInt(m[1]!, 16);
  return [(n >> 16) & 255, (n >> 8) & 255, n & 255];
}

const toHex = (c: readonly number[]) => `#${c.map((v) => Math.round(v).toString(16).padStart(2, "0")).join("")}`;

/** `a` blended `t` of the way towards `b`. */
function mix(a: string, b: string, t: number): string {
  const x = rgb(a);
  const y = rgb(b);
  if (!x || !y) return a;
  return toHex(x.map((v, i) => v + (y[i]! - v) * t));
}

function token(name: string, fallback: string): string {
  const v = getComputedStyle(document.documentElement).getPropertyValue(name).trim();
  return HEX.test(v) ? v : fallback;
}

/** Chart colours for the current scheme: the IDE's accents, softened towards the page in dark schemes. */
export function chartVariables(dark: boolean): Record<string, string> {
  const bg = token("--bg-editor", dark ? "#1e1f22" : "#ffffff");
  const fg = token("--fg-strong", dark ? "#dfe1e5" : "#000000");
  const base = [
    token("--accent", "#3574f0"),
    token("--success", "#5fb865"),
    token("--warning", "#e08855"),
    "#9b6fe8",
    token("--danger", "#e55765"),
    token("--syn-number", "#2aacb8"),
    "#d4a72c",
    "#c77dbb",
  ];
  const palette = base.map((c) => (dark ? mix(c, bg, 0.18) : mix(c, "#ffffff", 0.08)));
  const soft = base.map((c) => mix(c, bg, dark ? 0.62 : 0.78));
  const vars: Record<string, string> = {
    pieStrokeColor: bg,
    pieOuterStrokeColor: bg,
    pieTitleTextColor: fg,
    pieSectionTextColor: dark ? "#ffffff" : "#ffffff",
    pieLegendTextColor: fg,
    taskBkgColor: soft[0]!,
    taskBorderColor: palette[0]!,
    taskTextColor: fg,
    taskTextLightColor: fg,
    taskTextOutsideColor: fg,
    taskTextDarkColor: fg,
    activeTaskBkgColor: soft[1]!,
    activeTaskBorderColor: palette[1]!,
    doneTaskBkgColor: mix(fg, bg, 0.75),
    doneTaskBorderColor: mix(fg, bg, 0.55),
    critBkgColor: soft[4]!,
    critBorderColor: palette[4]!,
    sectionBkgColor: mix(fg, bg, 0.94),
    sectionBkgColor2: bg,
    altSectionBkgColor: bg,
    gridColor: mix(fg, bg, 0.8),
    todayLineColor: palette[4]!,
    titleColor: fg,
  };
  palette.concat(palette.slice(0, 4)).forEach((c, i) => {
    vars[`pie${i + 1}`] = c;
  });
  // Mind maps, timelines, journeys and kanban colour their branches with cScale.
  soft.concat(soft.slice(0, 4)).forEach((c, i) => {
    vars[`cScale${i}`] = c;
    vars[`cScaleLabel${i}`] = fg;
    vars[`cScalePeer${i}`] = palette[i % palette.length]!;
  });
  return vars;
}

const initialize = mermaid.initialize.bind(mermaid);

const studioMermaid: typeof mermaid = Object.assign(Object.create(mermaid) as typeof mermaid, {
  initialize(config: Config) {
    const dark = Boolean((config.themeVariables as Record<string, unknown> | undefined)?.darkMode);
    initialize({
      ...config,
      themeVariables: { ...chartVariables(dark), ...config.themeVariables },
      // Laid out at about the reading column's width, so the chart is not shrunk below legibility.
      gantt: { useWidth: 880, fontSize: 13, sectionFontSize: 13, barHeight: 24, barGap: 6, ...config.gantt },
    });
  },
});

export default studioMermaid;
