# 发布到 JetBrains Marketplace

这篇指南写给负责发版的维护者，覆盖第一次上架和之后每个版本的发布。JetBrains Marketplace 规定插件的第一个版本必须在网页上手动上传；审核通过后，后续版本可以用 Gradle 的 `publishPlugin` 任务发布。每个版本，包括更新，都会经过 JetBrains 的人工审核。

步骤依据 JetBrains 官方文档整理，签名和打包命令已在本仓库实际运行过。官方文档：[Publishing a Plugin](https://plugins.jetbrains.com/docs/intellij/publishing-plugin.html)、[Plugin Signing](https://plugins.jetbrains.com/docs/intellij/plugin-signing.html)、[Uploading a new plugin](https://plugins.jetbrains.com/docs/marketplace/uploading-a-new-plugin.html)、[Approval Guidelines](https://plugins.jetbrains.com/docs/marketplace/jetbrains-marketplace-approval-guidelines.html)。

## 一次性准备

这些事只在第一次发布前做一次。

### 创建 Vendor

1. 用 JetBrains Account 登录 [JetBrains Marketplace](https://plugins.jetbrains.com)。
2. 第一次上传插件前，Marketplace 会要求接受 JetBrains Marketplace Developer Agreement，并创建 Vendor profile。Vendor 的网站和联系邮箱必须真实可用，审核时会检查。
3. 按要求声明账号在欧洲经济区消费者保护法下是否属于 trader。

`plugin.xml` 中的 `<vendor>` 目前是 `Refinex`，网址 `https://github.com/Refinex-Space`，与 Vendor profile 保持一致。

### 生成签名证书

Marketplace 要求插件发布前签名，IDE 安装时会用签名确认插件没有被改动过。用 OpenSSL 生成私钥和证书链：

```sh
mkdir -p ~/.markdown-studio-signing && cd ~/.markdown-studio-signing
openssl genpkey -aes-256-cbc -algorithm RSA -out private_encrypted.pem -pkeyopt rsa_keygen_bits:4096
openssl rsa -in private_encrypted.pem -out private.pem
openssl req -key private.pem -new -x509 -days 365 -out chain.crt
```

第一条命令会要求设置私钥密码，记下它；后两条命令用它导出未加密的 `private.pem`，再生成有效期 365 天的证书链 `chain.crt`。签名时使用加密的 `private_encrypted.pem` 加密码，所以证书生成后可以删掉 `private.pem`，磁盘上不留未加密的私钥。证书过期后，重新执行后两条命令。

**私钥、密码和下面的令牌都不能提交到仓库。** 把它们放在仓库之外（例如上面的目录），在 CI 中使用加密的 secret。

### 生成发布令牌

在 Marketplace 个人页面的 My Tokens 中生成一个 Personal Access Token。令牌只显示一次，生成后立即保存。第一次手动上传用不到它，后续用 Gradle 发布时需要。

## 上架前检查

审核会对照 Approval Guidelines 检查下面几项。插件当前的状态：

| 检查项 | 要求 | 当前状态 |
| --- | --- | --- |
| 名称 | 不超过 30 个字符，只用拉丁字符，不含 Plugin、IntelliJ、JetBrains 或 JetBrains 产品名 | `Markdown Studio`，符合 |
| 图标 | 40×40 的 SVG，不同于模板图标，不像 JetBrains 产品图标 | `META-INF/pluginIcon.svg` 和 `pluginIcon_dark.svg`，符合 |
| 描述 | 英文写在最前面，格式正确，没有失效链接 | `plugin.xml` 的 `<description>`，只有英文，符合 |
| 更新说明 | 去掉模板占位文字，只写和本版有关的内容 | 构建时从 `CHANGELOG.md` 生成，每次发版前检查内容 |
| 许可证 | 提供 EULA；选择开源许可证时要附源码地址 | Apache-2.0，源码地址见 `gradle.properties` 的 `pluginRepositoryUrl` |
| 第三方内容 | 只分发自己拥有或获得许可的内容 | 图标、依赖库、字体和配色的来源与许可证列在 `THIRD_PARTY_LICENSES.md` |
| 隐私 | 处理个人数据或遥测前要征得用户同意 | 插件不收集任何数据 |
| 技术要求 | 通过 Plugin Verifier，不使用 JetBrains 内部 API | `./gradlew verifyPlugin` 通过 |

选择开源许可证时，`https://github.com/Refinex-Space/markdown-studio` 必须是公开仓库。第一次上传前，先把仓库推送到 GitHub 并设为公开。

## 准备一个版本

每次发布都在仓库根目录按这个顺序做：

1. 确认 Gradle 运行在 JDK 25 上：`./gradlew -version` 输出的 Launcher JVM 应为 25，Daemon JVM 指向一个 JDK 25 的目录。插件字节码的版本跟随这个 JDK，用更高版本构建的包装进 IDE 2026.2 后无法加载，原因见[本地开发与调试](development.md#准备环境)。
2. 修改 `gradle.properties` 的 `version`。Marketplace 不接受重复的版本号。
3. 在 `CHANGELOG.md` 的 `## [Unreleased]` 下写好本版的改动。
4. 运行 `./gradlew patchChangelog`。它把 `[Unreleased]` 改成 `## [<版本>] - <日期>`，并在上方留出新的空 `[Unreleased]`。之后构建时，`plugin.xml` 的更新说明取自这一版的小节。
5. 运行检查并打包：

   ```sh
   ./gradlew check verifyPlugin buildPlugin
   ```

6. 在一个干净的 IDE 里从磁盘安装 `build/distributions/markdown-studio-<版本>.zip` 试用一遍，步骤见[在本地 IDE 安装 Markdown Studio](local-install.md)。

## 第一次上传

1. 回到仓库根目录，设置签名用的环境变量，生成并校验签名后的安装包：

   ```sh
   cd /path/to/markdown-studio
   export CERTIFICATE_CHAIN="$(cat ~/.markdown-studio-signing/chain.crt)"
   export PRIVATE_KEY="$(cat ~/.markdown-studio-signing/private_encrypted.pem)"
   export PRIVATE_KEY_PASSWORD='<私钥密码>'
   ./gradlew signPlugin
   ./gradlew verifyPluginSignature
   ```

   签名后的文件是 `build/distributions/markdown-studio-<版本>-signed.zip`。`verifyPluginSignature` 要单独运行：和 `signPlugin` 写在同一条命令里时，Gradle 会因为任务依赖检查而报错。

2. 登录 Marketplace，打开右上角的账户菜单，选择 Upload plugin。
3. 选择发布用的 Vendor，上传 `-signed.zip`。
4. 填写插件信息：许可证选 Apache-2.0 并填写源码地址；标签选 Markdown、Editor 等和插件相关的标签，它们会成为搜索时的筛选条件。想先让少数人试用，勾选 Hidden，插件通过审核后也不会公开展示。
5. 提交，等待审核。3 到 4 个工作日没有回复，可以写信给 marketplace@jetbrains.com。

## 发布后续版本

第一个版本通过审核之后，按“准备一个版本”做完检查，在仓库根目录先签名并校验，再用 Gradle 发布：

```sh
export PUBLISH_TOKEN='<Personal Access Token>'
export CERTIFICATE_CHAIN="$(cat ~/.markdown-studio-signing/chain.crt)"
export PRIVATE_KEY="$(cat ~/.markdown-studio-signing/private_encrypted.pem)"
export PRIVATE_KEY_PASSWORD='<私钥密码>'
./gradlew signPlugin
./gradlew verifyPluginSignature
./gradlew publishPlugin
```

`publishPlugin` 会先运行 `signPlugin`，再把签名后的安装包上传到 Marketplace 的默认渠道。`build.gradle.kts` 里没有写 publishing 和 signing 配置，用的是 IntelliJ Platform Gradle Plugin 的默认值：令牌读环境变量 `PUBLISH_TOKEN`，签名读 `CERTIFICATE_CHAIN`、`PRIVATE_KEY` 和 `PRIVATE_KEY_PASSWORD`。

**`CERTIFICATE_CHAIN` 或 `PRIVATE_KEY` 没有设置时，`signPlugin` 会被跳过**，`publishPlugin` 照样上传未签名的包，而 Marketplace 要求插件签名。发布前单独运行的 `signPlugin` 和 `verifyPluginSignature` 就是为了拦住这种情况：`build/distributions/` 下出现 `-signed.zip` 且校验通过，再执行 `publishPlugin`。

新版本同样要经过审核，通过后，装有插件的用户会在 IDE 中收到更新提示。

### 发布测试版

想让一部分用户先试用新版本，可以发布到单独的渠道。在 `build.gradle.kts` 中加入：

```kotlin
intellijPlatform {
    publishing {
        channels = listOf("beta")
    }
}
```

渠道相当于一个独立的插件仓库。用户需要在 Settings | Plugins 的齿轮菜单 Manage Plugin Repositories… 中添加 `https://plugins.jetbrains.com/plugins/beta/list`，才能收到 beta 渠道的版本。这段配置会作用于之后的每次发布，发正式版前要把它删掉。

## 确认发布成功

- Marketplace 上的插件页面显示新版本，状态为已通过审核。
- 在一个没有装过插件的 IDE 中，打开 Settings | Plugins 的 Marketplace 标签，搜索 Markdown Studio，能够安装并正常打开 Markdown 文件。

## 常见问题

**`publishPlugin` 报认证失败。** 令牌错误、已撤销，或者令牌所属账号不是这个插件的 Vendor 成员。确认 `PUBLISH_TOKEN` 的值，必要时到 My Tokens 重新生成。

**上传时提示版本已存在。** `gradle.properties` 中的 `version` 没有改。改成新版本号，重新运行“准备一个版本”中的步骤。

**审核没有通过。** 审核邮件会写明原因，常见的是描述格式、图标、许可证或 Vendor 信息不符合 Approval Guidelines。按邮件修改后重新提交。
