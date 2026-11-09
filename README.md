# Markdown Studio

Language: English | [中文](./README.zh-CN.md)

A Markdown workspace for JetBrains IDEs: write in the rendered document (Live Preview), keep the
source beside a preview, and edit with the IDE's own keys, undo and version control.

## Features

- **Four layouts** per file, switched from the editor toolbar or Find Action:
  Live Preview, Source (the IDE's own editor), Source and Preview, Preview.
- **Live Preview**: headings, emphasis, links, images, lists, task lists, tables, code blocks,
  KaTeX math, Mermaid diagrams, GitHub callouts, footnotes and front matter render in place;
  the Markdown shows only around the caret.
- **Slash commands** (`/`), a **formatting toolbar** over selections, **table editing** with row
  and column menus, a **language picker** for code blocks, an **image and diagram viewer**,
  the document **outline**, back-to-top, and **find and replace**.
- **IDE integration**: edits are IDE commands on the file's document (undo / redo, local history,
  VCS); Format / Paragraph / Insert in the editor's context menu; every command in Find Action and
  Settings | Keymap; the IDE's editor actions (Duplicate Line, Move Line, Extend Selection, Join
  Lines, Find, Select All…) on their usual shortcuts; Cut / Copy / Paste through the IDE clipboard;
  links open in the IDE or the browser.
- **Your look**: theme, editor colour scheme (code blocks highlight in it), fonts, ligatures, IDE
  zoom; English and Simplified Chinese.
- **Welcome tab** after installation (again from Help | Markdown Studio Welcome): the tour, the
  layout new files open in, the keys to start with.
- **Settings | Tools | Markdown Studio**: open Markdown files in Markdown Studio or the IDE's
  editors, the layout new files open in, scroll sync, language, reading width, text size, the
  outline, the formatting toolbar, web images.

Requires IntelliJ Platform 2026.2 or later, with a JCEF runtime.

## Installing

Once the plugin is on JetBrains Marketplace, search for Markdown Studio in Settings | Plugins |
Marketplace. To install a local build, see [Installing in a local IDE](docs/guides/local-install.md)
(in Chinese).

## Building

Requirements: JDK 25 to run Gradle (the plugin's bytecode follows it and must match the Java 25
runtime of IDE 2026.2), Node.js 22.12+ and pnpm 10.

```sh
./gradlew buildPlugin        # build/distributions/markdown-studio-<version>.zip
./gradlew check              # plugin tests, page typecheck and tests
./gradlew verifyPlugin       # IntelliJ Plugin Verifier against the recommended IDEs
./gradlew runIde             # a sandbox IDE with the plugin (page DevTools on localhost:9222)
./gradlew runIde -PideProject=/path/to/project
```

The page (`web/`) builds as part of `processResources`.

## Documentation

The documentation in [docs/](docs/README.md) is in Chinese:

- [Architecture](docs/design/architecture.md): how the plugin is put together, document sync, key
  dispatch, design decisions
- [Development and debugging](docs/guides/development.md)
- [Installing in a local IDE](docs/guides/local-install.md)
- [Publishing to JetBrains Marketplace](docs/guides/publishing.md)

## How it works

The IDE side (Kotlin) registers the editor and owns the document, keys, menus and settings. The
page side (TypeScript) runs in the IDE's embedded browser (JCEF) and renders and edits. The two
talk through JSON messages.

```
IDE (Kotlin)                                        Page (JCEF, TypeScript)
MarkdownStudioEditor (TextEditorWithPreview)
 ├─ TextEditor  ── Source / Split                    web/core: the editor core
 └─ MarkdownPageEditor ── MarkdownPage ◀── JSON ──▶  web/src/host, web/src/editor:
      DocumentAuthority  (versions, undo groups)       the IDE channel, the CodeMirror editor
      PageResources      (https://markdown-studio.localhost/ → /web in the jar)
      PageSkeleton       (loading skeleton until the page has drawn)
      ThemeTokens        (IDE look → page tokens)
      PageMenus / PageClipboard
```

- **Document sync**: the IDE document is the authority of a
  [@codemirror/collab](https://codemirror.net/docs/ref/#collab) session. The page pushes its
  changes with the version it has seen; the IDE applies them as commands (typing in one place is
  one undo step) or answers with the changes the page has not seen, which it rebases over. Undo,
  redo and every other change of the document reach the page the same way.
- **Keys**: the IDE dispatches keys first. Markdown commands (`MarkdownStudio.*`), the IDE's
  editor actions in the page (`MarkdownStudio.Page.*`, registered with `use-shortcut-of`) and
  Undo / Redo win their shortcuts while Live Preview edits (an `ActionPromoter`); other keys
  reach the page.
- **Page project**: `web/core` is the editor core; `web/src/shims` holds the IDE's version of the
  core's host modules (IPC, keymap, menus, editor registry, opening files), which win over the
  core module at the same path.

## License

Apache License 2.0. Third-party material the plugin bundles (icons, web libraries, fonts,
colour palettes) and its licences: [THIRD_PARTY_LICENSES.md](THIRD_PARTY_LICENSES.md).
