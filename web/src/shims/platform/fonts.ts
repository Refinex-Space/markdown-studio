/** Resolves once `family` can be measured at `size` (Mermaid measures labels with it). */
export async function fontsLoaded(family: string, size: number): Promise<void> {
  const fonts = typeof document !== "undefined" ? document.fonts : undefined;
  if (!fonts?.load || !family) return;
  try {
    await Promise.all([fonts.load(`${size}px ${family}`), fonts.load(`bold ${size}px ${family}`)]);
  } catch {
    /* an unparsable list measures with whatever is available */
  }
}
