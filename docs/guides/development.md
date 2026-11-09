# 本地开发与调试

这篇指南带你从克隆仓库到在沙箱 IDE 里调试插件和页面，适合要修改 Markdown Studio 的开发者。插件的整体结构见[架构设计](../design/architecture.md)。

## 准备环境

| 工具 | 版本 | 说明 |
| --- | --- | --- |
| JDK | 25 | 用于运行 Gradle。构建没有配置 Java toolchain，插件字节码的版本跟随运行 Gradle 的 JDK；IntelliJ 平台 2026.2 运行在 Java 25 上，用更高版本的 JDK 构建出的插件无法在其中加载 |
| Node.js | 22.12 及以上 | 见 `web/package.json` 的 `engines` |
| pnpm | 10 | `web/package.json` 固定为 `pnpm@10.33.0`，可以用 `corepack enable` 安装 |

Gradle 使用仓库自带的 wrapper（`./gradlew`，Gradle 9.8.1），不需要另外安装。它用 `JAVA_HOME` 指向的 JDK 运行；在 IntelliJ IDEA 中运行 Gradle 任务时，用的是 Settings | Build, Execution, Deployment | Build Tools | Gradle 中的 Gradle JVM。两处都要设成 JDK 25。用 `./gradlew -version` 确认：Launcher JVM 应为 25。

## 第一次构建

```sh
git clone https://github.com/Refinex-Space/markdown-studio.git
cd markdown-studio
./gradlew buildPlugin
```

第一次构建会下载 IntelliJ IDEA 2026.2.3 和 Gradle 依赖，在 `web/` 下执行 `pnpm install --frozen-lockfile` 并构建页面。完成后，`build/distributions/markdown-studio-<版本>.zip` 就是插件安装包，版本号取自 `gradle.properties` 的 `version`。

## 运行沙箱 IDE

```sh
./gradlew runIde                              # 启动装有插件的沙箱 IDE
./gradlew runIde -PideProject=/path/to/project  # 启动时直接打开一个项目
```

沙箱 IDE 的配置、插件和日志放在 `.intellijPlatform/sandbox/` 下，和你日常使用的 IDE 互不影响。想换一份干净的沙箱，删除这个目录后重新运行即可。

在 IntelliJ IDEA 中打开本仓库后，`.run/` 目录提供了三个运行配置：Run Plugin（`runIde`）、Run Tests（`check`）和 Run Verifications（`verifyPlugin`）。用 Debug 方式运行 Run Plugin，就能在 Kotlin 代码里打断点。

要在本机已安装的 IDE 里试用当前代码，指定它的安装路径：

```sh
./gradlew runLocalIde -PlocalIdePath="/Applications/IntelliJ IDEA.app"
```

## 调试页面

沙箱 IDE 打开了页面的远程调试端口 9222：

1. 在 Chrome 中打开 `chrome://inspect`。
2. 点击 Configure，加入 `localhost:9222`。
3. 在沙箱 IDE 里打开一个 Markdown 文件，它对应的页面会出现在列表中，点击 inspect 打开 DevTools。

页面代码在 `web/` 下。改完页面代码后重新运行 `runIde`，Gradle 会重新构建页面并打进插件。

## 运行测试

```sh
./gradlew check                   # Kotlin 测试，加上页面的类型检查和测试
./gradlew test                    # 只跑 Kotlin 测试
pnpm --dir web check              # 只跑页面：tsc 和 Vitest
pnpm --dir web exec vitest run src/editor   # 只跑某个目录的页面测试
./gradlew verifyPlugin            # 用 Plugin Verifier 检查推荐的 IDE 版本
```

`./gradlew check` 是提交前要通过的检查。修改了 `plugin.xml`、平台 API 调用或依赖版本时，再跑一次 `verifyPlugin`。

需要检查运行中的插件（比如按键、菜单、布局切换）时，用 `./gradlew runIdeForUiTests` 启动沙箱 IDE。它额外装有 robot-server，可以通过 `http://127.0.0.1:8082` 在 IDE 进程里执行脚本。

## 常见改动的位置

| 改动 | 需要修改的地方 |
| --- | --- |
| 新增 Markdown 命令 | 在 `web/core/features/viewers/markdown/livePreview/actions.ts` 中注册页面命令，要出现在右键菜单的话，把它加进同一文件里 `registerMenuGroup` 的对应分组；命令标题的中文写进 `web/core/platform/locales/text/zh.json`；在 `plugin.xml` 的“格式 / 段落 / 插入”分组下增加 `MarkdownStudio.<Name>` 动作；在两个资源包中补 `action.MarkdownStudio.<Name>.text` 和 `.description`；更新 `MarkdownCommandActionTest` 中的命令数量（当前 26） |
| 新增页面内的 IDE 编辑动作 | 在 `web/src/editor/editingCommands.ts` 的 `EDITING_COMMANDS` 中加命令；在 `plugin.xml` 增加带 `use-shortcut-of` 的 `MarkdownStudio.Page.<Name>`；补资源包文案；更新 `MarkdownCommandActionTest` 中的动作数量（当前 17） |
| 新增设置项 | `MarkdownStudioSettings.Options` 和对应设置页；页面需要响应时，同时扩展 `src/main/kotlin/.../page/Protocol.kt` 和 `web/src/host/protocol.ts` 中的 `PagePrefs` |
| 新增 IDE 与页面之间的消息 | 同时修改 `Protocol.kt` 和 `web/src/host/protocol.ts`，两边的 `type` 名称保持一致 |
| 调整 IDE 颜色、字体到页面的映射 | `src/main/kotlin/.../page/ThemeTokens.kt` |
| 调整页面样式 | 宿主专属的覆盖写在 `web/src/app/host.css`，编辑器本身的样式直接改 `web/core` |
| 修改欢迎页 | 文案在两个资源包的 `welcome.*` 键下；页面在 `src/main/resources/welcome/`（`index.html`、`welcome.css`、`welcome.js`），导览文档是同目录的 `tour_en.md` 和 `tour_zh.md`；页面数据由 `WelcomePage.kt` 中的 `WelcomeData` 组装 |
| 调整页面的内边距、正文行高或大纲宽度 | 同步修改 `PageSkeleton` 中对应的常量，否则骨架屏和正文对不齐；常量与页面样式的对应关系见[架构设计](../design/architecture.md#关键取舍) |

新增命令或动作后漏改 `plugin.xml`，`web/src/actionParity.test.ts` 会失败；中英文资源包的键不一致，`MarkdownStudioSettingsTest` 会失败；命令数量没改，`MarkdownCommandActionTest` 会失败。右键菜单分组和页面中文文案没有测试覆盖，改完在沙箱 IDE 里切换到中文看一遍。

## 常见问题

**Gradle 报 `A problem occurred starting process 'command 'pnpm''`。** Gradle 守护进程的 PATH 里找不到 pnpm，常见于用 nvm 安装 Node、守护进程又是从非交互 shell 启动的情况。在能找到 pnpm 的终端里执行 `./gradlew --stop` 后重新构建，或者显式指定 pnpm 的路径：

```sh
./gradlew buildPlugin -PpnpmCommand="$(command -v pnpm)"
```

**沙箱里的 Markdown 文件用 IDE 自带的编辑器打开。** 检查 Settings | Tools | Markdown Studio 中的 Open Markdown files in Markdown Studio 是否勾选，再确认 IDE 运行时带有 JCEF（Help | Find Action，搜索 Choose Boot Java Runtime for the IDE）。已经打开的标签需要关闭后重新打开。

**编辑区一直显示骨架屏，或者显示空白。** 页面没有正常启动。打开 Help | Show Log in Finder（Windows 和 Linux 上是 Show Log in Explorer / Files），在 `idea.log` 中搜索 `Markdown Studio`，再用 DevTools 查看页面控制台的报错。骨架屏在 10 秒后会自动撤掉，露出页面当时的状态。
