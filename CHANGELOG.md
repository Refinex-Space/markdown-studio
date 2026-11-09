<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# Markdown Studio Changelog

## [Unreleased]

## [0.1.1] - 2026-11-08

### Fixed

- Mermaid diagrams in Live Preview redraw in the new colours when the IDE theme changes
- Table handles in Live Preview no longer stay at their old places after the table or the window changes width
- No deprecated API on IntelliJ IDEA 2026.3: the page's saved state is read through the new `FileEditorProvider.readState` overload

## [0.1.0] - 2026-11-08

### Added

- Markdown files open in Markdown Studio: Live Preview, Source, Source and Preview, and Preview layouts, switched from the editor toolbar or Find Action
- Live Preview: headings, emphasis, links, images (local and web), lists, task lists, tables, code blocks, KaTeX math, Mermaid diagrams, GitHub callouts, footnotes, front matter and character references render in place
- Slash commands, the selection formatting toolbar, table editing with row and column menus, a code block language picker, the image and diagram viewer
- The slash menu groups the kinds of a callout and the Mermaid diagrams in submenus (opened with the mouse or the arrow keys); a search lists every command
- Tooltips with their shortcuts on the formatting toolbar and on the page's buttons (copy code, image viewer, outline)
- The document outline beside the text, shown or hidden from the editor toolbar
- A loading skeleton in the document's shape while a Markdown file opens, fading into the text once it is drawn
- A welcome tab after installation (and from Help | Markdown Studio Welcome): the tour, the layout new files open in, what the plugin does and the keys to start with
- A find bar in Live Preview (match case, words, regex, count, replace)
- Select All takes the code block, front matter or math block at the caret first, then the document
- Format / Paragraph / Insert in the editor menu; every Markdown command in Find Action and the keymap (⌘B / Ctrl+B, ⌘I / Ctrl+I, ⌘⇧X / Ctrl+Shift+X by default)
- The IDE's editor actions in Live Preview on their own shortcuts: Duplicate Line, Delete Line, Move Line / Statement, Extend / Shrink Selection, Join Lines, Start New Line, Split Line, Toggle Case, Comment, Find, Replace
- Edits are IDE commands on the file's document: undo / redo, local history and version control as with any editor; several editors of one file stay in step
- The IDE's look: editor colour scheme (code blocks highlight in it), interface theme, fonts and ligatures, IDE zoom, caret blinking and bracket pairing
- English and Simplified Chinese, following the IDE or chosen in the settings; menus, slash commands, table actions, tooltips and the outline switch at once
- Settings | Tools | Markdown Studio: the editor for Markdown files, the layout new files open in, scroll sync, language; Appearance: reading width, text size, outline, web images; Editing: the formatting toolbar, a link to the keymap

[Unreleased]: https://github.com/Refinex-Space/markdown-studio/compare/v0.1.1...HEAD
[0.1.1]: https://github.com/Refinex-Space/markdown-studio/compare/v0.1.0...v0.1.1
[0.1.0]: https://github.com/Refinex-Space/markdown-studio/commits/v0.1.0
