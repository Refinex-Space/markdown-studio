/**
 * The page's language (Settings | Tools | Markdown Studio, or the IDE's), with the terms a
 * Markdown editor uses where the core's shared catalog words them for a whole IDE.
 */
import { setLocale, useI18n } from "@/platform/i18n";

const TERMS: Record<string, Record<string, string>> = {
  zh: {
    Table: "表格",
    Image: "图片",
    Images: "图片",
  },
};

export async function setPageLocale(locale: string): Promise<void> {
  const resolved = await setLocale(locale);
  const terms = TERMS[resolved];
  if (terms) useI18n.setState((s) => ({ texts: { ...s.texts, ...terms } }));
}
