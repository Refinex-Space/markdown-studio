# Markdown Studio

语言：[English](./README.md) | 中文

Markdown Studio 是 JetBrains IDE 的 Markdown 编辑插件：在渲染好的文档里直接编辑，或者源码和预览并排，同时使用 IDE 原有的快捷键、撤销和版本控制。

## 功能

- **四种布局**，可在编辑器工具栏或 Find Action 中切换：实时预览、源码（IDE 原生编辑器）、源码和预览、预览。
- **实时预览**：标题、强调、链接、图片、列表、任务列表、表格、代码块、KaTeX 公式、Mermaid 图表、GitHub 提示块、脚注和 front matter 在原位渲染，只有光标所在处显示 Markdown 源码。
- **编辑辅助**：斜杠命令（`/`）、选区格式工具栏、带行列菜单的表格编辑、代码块语言选择、图片和图表查看器、侧边大纲和回到顶部、查找替换。
- **IDE 集成**：
  - 每次编辑都是文件 Document 上的 IDE 命令，撤销、重做、本地历史和版本控制照常工作。
  - 右键菜单里有「格式 / 段落 / 插入」，所有命令都能在 Find Action 和 Keymap 中找到。
  - 复制行、移动行、扩展选区、合并行、查找、全选等 IDE 编辑器动作使用原来的快捷键。
  - 剪切、复制、粘贴走 IDE 剪贴板，链接在 IDE 或浏览器中打开。
- **跟随 IDE 外观**：主题、编辑器配色（代码块按它高亮）、字体、连字、IDE 缩放；界面支持英文和简体中文。
- **欢迎页**：安装后自动打开，可以打开导览文档、选择新文件的默认布局、查看常用快捷键；之后可在 Help | Markdown Studio Welcome 再次打开。
- **设置**（Settings | Tools | Markdown Studio）：是否接管 Markdown 文件、新文件的默认布局、滚动同步、语言、阅读宽度、字号、大纲、格式工具栏、网络图片。

需要 IntelliJ 平台 2026.2 及以上版本，且 IDE 运行时带有 JCEF。

## 安装

插件上架 JetBrains Marketplace 后，在 Settings | Plugins 的 Marketplace 标签中搜索 Markdown Studio 安装。安装本地构建的 zip，见[在本地 IDE 安装](docs/guides/local-install.md)。

## 构建

环境要求：JDK 25（用于运行 Gradle；插件字节码跟随这个 JDK，需与 IDE 2026.2 的 Java 25 运行时一致）、Node.js 22.12 及以上、pnpm 10。

```sh
./gradlew buildPlugin        # 产物：build/distributions/markdown-studio-<version>.zip
./gradlew check              # 插件测试、页面类型检查与测试
./gradlew verifyPlugin       # 用 IntelliJ Plugin Verifier 校验推荐的 IDE 版本
./gradlew runIde             # 启动装有插件的沙箱 IDE（页面 DevTools：localhost:9222）
./gradlew runIde -PideProject=/path/to/project
```

页面（`web/`）在 `processResources` 阶段一并构建。

## 文档

- [架构设计](docs/design/architecture.md)：插件的组成、文档同步、按键分发、关键取舍
- [本地开发与调试](docs/guides/development.md)
- [在本地 IDE 安装](docs/guides/local-install.md)
- [发布到 JetBrains Marketplace](docs/guides/publishing.md)

## 实现概要

IDE 一侧（Kotlin）负责编辑器注册、文档、按键、菜单和设置；页面一侧（TypeScript）运行在 IDE 内置的浏览器（JCEF）中，负责渲染和编辑交互，两边通过 JSON 消息通信。

- **文档同步**：IDE 的 Document 是一次 [@codemirror/collab](https://codemirror.net/docs/ref/#collab) 会话的权威端。页面提交改动时附带自己已知的版本号；IDE 把改动作为命令执行（同一处的连续输入合并为一步撤销），或者返回页面还没收到的改动，由页面变基后重新提交。撤销、重做以及 Document 的其他变化都通过同一条通道同步到页面。
- **按键**：IDE 先处理按键。Live Preview 编辑时，Markdown 命令（`MarkdownStudio.*`）、页面内的 IDE 编辑器动作（`MarkdownStudio.Page.*`，通过 `use-shortcut-of` 注册）以及撤销、重做优先占用各自的快捷键；其余按键交给页面。
- **页面工程**：`web/core` 是编辑器内核；`web/src/shims` 放内核宿主模块在 IDE 中的实现，与内核同路径时优先生效。

## 许可

Apache License 2.0。插件打包的第三方素材（图标、页面依赖库、字体、配色）及其许可证见 [THIRD_PARTY_LICENSES.md](THIRD_PARTY_LICENSES.md)。
