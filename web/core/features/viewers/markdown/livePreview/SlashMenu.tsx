/**
 * The slash menu's list (see `slash.ts`): sections of Markdown blocks, or ranked search results.
 * A submenu row (callouts, diagrams) opens its kinds beside it, on hover or the keyboard.
 */
import { useLayoutEffect, useMemo, useRef, useState } from "react";
import clsx from "clsx";
import { Icon, Popup } from "@/ui";
import { useTx } from "@/platform/i18n";
import { isFolder, slashGroups, type SlashCommand } from "./catalog";
import { closeSlash, openFolder, runSlash, useSlash } from "./slash";
import styles from "./SlashMenu.module.css";

function Row(props: {
  index: number;
  active: boolean;
  icon: string;
  label: string;
  hint?: string;
  submenu?: boolean;
  expanded?: boolean;
  onHover: () => void;
  onPick: () => void;
}) {
  return (
    <div
      data-index={props.index}
      role={props.submenu ? "menuitem" : "option"}
      aria-selected={props.active}
      aria-haspopup={props.submenu || undefined}
      aria-expanded={props.submenu ? props.expanded : undefined}
      className={clsx(styles.item, props.active && styles.active, props.expanded && styles.expanded)}
      onMouseMove={props.onHover}
      onMouseDown={(e) => {
        e.preventDefault();
        props.onPick();
      }}
    >
      <span className={styles.icon}>
        <Icon name={props.icon} size={16} />
      </span>
      <span className={styles.label}>{props.label}</span>
      {props.submenu ? (
        <span className={styles.chevron}>
          <Icon name="chevron-right" size={14} />
        </span>
      ) : (
        props.hint && <span className={styles.hint}>{props.hint}</span>
      )}
    </div>
  );
}

export function SlashMenu() {
  const tx = useTx();
  const state = useSlash();
  const { view, query, active, sub, subActive, rect } = state;
  const groups = useMemo(() => slashGroups(query), [query]);
  const list = useRef<HTMLDivElement>(null);
  const folder = openFolder(state);
  // A hint every kind shares ("mermaid" for each diagram) tells nothing beside the others.
  const folderHints = folder && new Set(folder.items.map((i) => i.hint)).size > 1;
  const [folderRow, setFolderRow] = useState<HTMLElement | null>(null);

  useLayoutEffect(() => {
    list.current?.querySelector<HTMLElement>(`[data-index="${active}"]`)?.scrollIntoView({ block: "nearest" });
  }, [active, groups]);

  // The submenu hangs off its row.
  useLayoutEffect(() => {
    setFolderRow(folder ? (list.current?.querySelector<HTMLElement>(`[data-index="${active}"]`) ?? null) : null);
  }, [folder, active, groups]);

  const anchor = useMemo(() => (rect ? { getBoundingClientRect: () => rect } : null), [rect]);
  if (!view || !anchor || !groups.length) return null;

  let index = 0;
  return (
    <Popup
      open
      anchor={anchor}
      placement="bottom-start"
      offset={6}
      autoFocus={false}
      restoreFocus={false}
      onClose={closeSlash}
      className={styles.popup}
      role="presentation"
    >
      <div ref={list} className={styles.list} role="listbox" aria-label={tx("Insert Block")}>
        {groups.map((g) => (
          <div key={g.section ?? "results"} role="group" aria-label={g.section ? tx(g.section) : undefined}>
            {g.section && <div className={styles.section}>{tx(g.section)}</div>}
            {g.items.map((entry) => {
              const i = index++;
              if (isFolder(entry)) {
                const expanded = sub === entry.id && active === i;
                return (
                  <Row
                    key={entry.id}
                    index={i}
                    active={i === active}
                    icon={entry.icon}
                    label={tx(entry.label)}
                    submenu
                    expanded={expanded}
                    onHover={() => !expanded && useSlash.setState({ active: i, sub: entry.id, subActive: 0 })}
                    onPick={() => useSlash.setState({ active: i, sub: entry.id, subActive: 0 })}
                  />
                );
              }
              return (
                <Row
                  key={entry.id}
                  index={i}
                  active={i === active}
                  icon={entry.icon}
                  label={tx(entry.label)}
                  hint={entry.hint}
                  onHover={() => (i !== active || sub) && useSlash.setState({ active: i, sub: null })}
                  onPick={() => runSlash(entry)}
                />
              );
            })}
          </div>
        ))}
      </div>
      {folder && folderRow && (
        <Popup
          open
          anchor={folderRow}
          placement="right-start"
          offset={4}
          autoFocus={false}
          restoreFocus={false}
          onClose={() => useSlash.setState({ sub: null, subActive: 0 })}
          className={clsx(styles.popup, styles.submenu)}
          role="presentation"
        >
          <div className={styles.list} role="listbox" aria-label={tx(folder.label)}>
            {folder.items.map((item: SlashCommand, i) => (
              <Row
                key={item.id}
                index={i}
                active={i === subActive}
                icon={item.icon}
                label={tx(item.short ?? item.label)}
                hint={folderHints ? item.hint : undefined}
                onHover={() => i !== subActive && useSlash.setState({ subActive: i })}
                onPick={() => runSlash(item)}
              />
            ))}
          </div>
        </Popup>
      )}
    </Popup>
  );
}
