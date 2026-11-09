# Third-party licenses

Markdown Studio is licensed under the Apache License 2.0 (see [LICENSE](LICENSE)). The plugin
bundles the following third-party material.

## JetBrains IntelliJ Platform icons

`web/core/ui/expui/**` contains SVG icons from the IntelliJ Platform "new UI" (expui) icon set,
copied unmodified from [intellij-community](https://github.com/JetBrains/intellij-community)
(`platform/icons/src/expui/**`), each keeping its original copyright header. Copyright JetBrains
s.r.o. and contributors. Apache License 2.0, the same text as [LICENSE](LICENSE). At render time `web/core/ui/icons.tsx` may
recolour monochrome icons to `currentColor`.

## Web libraries

The editor page (`web/`) bundles these npm packages; each package's licence file ships in its
package and the versions are pinned in `web/pnpm-lock.yaml`.

| Package | Licence |
| --- | --- |
| CodeMirror 6 (`@codemirror/*`) and Lezer (`@lezer/*`) | MIT |
| React, React DOM | MIT |
| KaTeX | MIT |
| Mermaid | MIT |
| marked | MIT |
| DOMPurify | MPL-2.0 or Apache-2.0 |
| Floating UI (`@floating-ui/react-dom`) | MIT |
| clsx | MIT |
| zustand | MIT |

## Fonts

Inter (`@fontsource-variable/inter`, Copyright 2016 The Inter Project Authors,
https://github.com/rsms/inter) and JetBrains Mono (`@fontsource-variable/jetbrains-mono`,
Copyright 2020 The JetBrains Mono Project Authors, https://github.com/JetBrains/JetBrainsMono)
are bundled as woff2 files under the SIL Open Font License 1.1.

## Solarized

The Solarized palettes in `web/core/styles/tokens.css` are adapted from
[Ethan Schoonover’s Solarized](https://github.com/altercation/solarized/tree/62f656a02f93c5190a8753159e34b385588d5ff3).
MIT license:

```text
Copyright (c) 2011 Ethan Schoonover

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
THE SOFTWARE.
```

## Atom One Light and One Dark

The One palettes and semantic syntax mapping in `web/core/styles/tokens.css` are adapted from
GitHub’s Atom One syntax and UI themes. Both syntax themes use the following MIT notice:

```text
Copyright (c) 2016 GitHub Inc.

Permission is hereby granted, free of charge, to any person obtaining
a copy of this software and associated documentation files (the
"Software"), to deal in the Software without restriction, including
without limitation the rights to use, copy, modify, merge, publish,
distribute, sublicense, and/or sell copies of the Software, and to
permit persons to whom the Software is furnished to do so, subject to
the following conditions:

The above copyright notice and this permission notice shall be
included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
```

Both UI themes use the following MIT notice:

```text
Copyright (c) 2014 GitHub Inc.

Permission is hereby granted, free of charge, to any person obtaining
a copy of this software and associated documentation files (the
"Software"), to deal in the Software without restriction, including
without limitation the rights to use, copy, modify, merge, publish,
distribute, sublicense, and/or sell copies of the Software, and to
permit persons to whom the Software is furnished to do so, subject to
the following conditions:

The above copyright notice and this permission notice shall be
included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
```

## Chinese UI terminology from Visual Studio Code

Some Simplified Chinese terms in `web/core/platform/locales/text/zh.json` follow the wording of
the Visual Studio Code Simplified Chinese language pack
([microsoft/vscode-loc](https://github.com/microsoft/vscode-loc), MIT), e.g. "未跟踪"
(untracked), "合并冲突" (merge conflicts), "已修改" (modified), "深色" / "浅色" (dark / light)
and "只读" (read-only).

```text
Visual Studio Code Localization Packs

Copyright (c) Microsoft Corporation

All rights reserved.

MIT License

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE
```
