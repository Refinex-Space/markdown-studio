# 在本地 IDE 安装 Markdown Studio

插件上架 JetBrains Marketplace 之前，或者要试用还没发布的版本时，可以把安装包直接装进自己的 IDE。整个过程大约两分钟，需要一个插件 zip 文件。

## 适用的 IDE

Markdown Studio 支持基于 IntelliJ 平台 2026.2 及以上版本（build 262 起）的 JetBrains IDE，IDE 运行时需要带 JCEF。插件只依赖平台和 JCEF 模块，IntelliJ IDEA、WebStorm、PyCharm 等都可以安装；目前用 Plugin Verifier 检查过的是 IntelliJ IDEA 2026.2 和 2026.3。

## 准备安装包

如果已经拿到 `markdown-studio-<版本>.zip`，跳到下一节。否则在仓库根目录构建：

```sh
./gradlew buildPlugin
```

构建环境的要求见[本地开发与调试](development.md)。安装包生成在 `build/distributions/markdown-studio-<版本>.zip`，不需要解压。

## 安装插件

1. 打开 Settings：macOS 上是 IntelliJ IDEA | Settings（⌘,），Windows 和 Linux 上是 File | Settings（Ctrl+Alt+S）。
2. 选择 Plugins，点击顶部的齿轮图标，选择 Install Plugin from Disk…。
3. 选中 zip 文件，点击 OK。
4. 回到 Plugins 页面点击 OK。IDE 提示重启时，重启 IDE。

本地构建的安装包没有签名，安装时 IDE 会提示插件未签名，确认继续即可。从 Marketplace 安装的正式版本是签过名的。

## 确认安装成功

- 第一次安装后，IDE 打开“欢迎使用 Markdown Studio”标签页。点击“打开导览”会打开一份示例文档。关掉后可以在 Help | Markdown Studio Welcome 中再次打开。
- 在 Settings | Plugins 的 Installed 标签下，User-installed 分组里能看到 Markdown Studio 和它的蓝色图标。
- 打开任意 `.md` 文件，编辑区先显示文档形状的加载骨架，随后出现渲染后的文档，可以直接在里面输入。
- 编辑器右上角的工具栏可以在 Live Preview、Source、Source and Preview、Preview 四种布局之间切换。
- Settings | Tools | Markdown Studio 下有 Markdown Studio、Appearance、Editing 三个设置页。

## 升级到新版本

重复安装步骤，选择新版本的 zip。IDE 会替换旧版本，重启后生效。

设置保存在 IDE 配置目录的 `options/markdown-studio.xml` 中，升级不影响已有设置。

## 停用和卸载

只想让 Markdown 文件改回用 IDE 自带的编辑器打开，不必卸载：在 Settings | Tools | Markdown Studio 中取消勾选 Open Markdown files in Markdown Studio。

完全停用或卸载插件：

1. 打开 Settings | Plugins，选中 Markdown Studio。
2. 停用：点击 Disable。卸载：展开 Disable 旁边的下拉箭头，选择 Uninstall。
3. 按提示重启 IDE。

## 常见问题

**Markdown 文件仍然用 IDE 自带的编辑器打开。** 已经打开的标签保持原来的编辑器，关闭后重新打开即可。插件只接管扩展名为 `md`、`markdown`、`mdown`、`mkd`、`mkdn`、`mdwn` 的文件。如果编辑器右上角有 Live Preview、Source 等布局按钮，说明插件已经接管，只是当前处在 Source 布局（IDE 的文本编辑器），点 Live Preview 切换即可；新文件默认用哪种布局，在 Settings | Tools | Markdown Studio 中设置。如果新打开的文件也没有这些按钮，检查 Settings | Tools | Markdown Studio 中的 Open Markdown files in Markdown Studio 是否勾选，再确认 IDE 运行时带有 JCEF：Help | Find Action，搜索 Choose Boot Java Runtime for the IDE，选择带 JCEF 的 JetBrains Runtime。

**安装时提示插件与当前 IDE 不兼容。** IDE 版本低于 2026.2。升级 IDE 后再安装。

**安装成功，但打开 Markdown 文件时插件没有生效，`idea.log` 里有 `UnsupportedClassVersionError`。** 安装包是用高于 25 的 JDK 构建的，IDE 无法加载其中的类。用 JDK 25 重新构建，见[本地开发与调试](development.md#准备环境)。

**编辑区一直显示骨架屏，或者显示空白。** 页面没有正常启动。骨架屏 10 秒后会自动撤掉。打开 Help | Show Log in Finder（Windows 和 Linux 上是 Show Log in Explorer / Files），在 `idea.log` 中搜索 `Markdown Studio`，把相关日志附在问题反馈里。
