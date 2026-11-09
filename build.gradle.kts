import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.jetbrains.intellij.platform")
    id("org.jetbrains.changelog")
}

dependencies {
    testImplementation("junit:junit:4.13.2")

    // https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-dependencies-extension.html
    intellijPlatform {
        intellijIdea("2026.2.3")
        bundledPlugin("com.intellij.modules.jcef")
        testFramework(TestFrameworkType.Platform)
        testBundledPlugins("com.intellij.modules.ultimate")
    }
}

intellijPlatform {
    pluginVerification {
        ides {
            recommended()
        }
    }
}

// The editor page (web/): the Markdown editor in JCEF, built with pnpm and packaged under /web.
val web = layout.projectDirectory.dir("web")
val pnpm = providers.gradleProperty("pnpmCommand")
    .orElse(if (System.getProperty("os.name").startsWith("Windows")) "pnpm.cmd" else "pnpm")

val webInstall by tasks.registering(Exec::class) {
    group = "web"
    description = "Installs the editor page's dependencies."
    workingDir(web)
    commandLine(pnpm.get(), "install", "--frozen-lockfile")
    inputs.files(web.file("package.json"), web.file("pnpm-lock.yaml"))
    outputs.file(web.file("node_modules/.modules.yaml"))
}

val webBuild by tasks.registering(Exec::class) {
    group = "web"
    description = "Builds the editor page."
    dependsOn(webInstall)
    workingDir(web)
    commandLine(pnpm.get(), "run", "build")
    inputs.dir(web.dir("src"))
    inputs.dir(web.dir("core"))
    inputs.files(
        web.file("index.html"),
        web.file("package.json"),
        web.file("pnpm-lock.yaml"),
        web.file("tsconfig.json"),
        web.file("vite.config.ts"),
    )
    outputs.dir(web.dir("dist"))
}

val webTest by tasks.registering(Exec::class) {
    group = "verification"
    description = "Type-checks the editor page and runs its tests."
    dependsOn(webInstall)
    workingDir(web)
    commandLine(pnpm.get(), "run", "check")
    inputs.dir(web.dir("src"))
    inputs.dir(web.dir("core"))
    inputs.file(layout.projectDirectory.file("src/main/resources/META-INF/plugin.xml"))
    outputs.upToDateWhen { false }
}

// The plugin's version for its own pages (`welcome/build.properties`).
val pluginVersion = providers.gradleProperty("version")

tasks {
    processResources {
        inputs.property("pluginVersion", pluginVersion)
        val version = pluginVersion.get()
        filesMatching("welcome/build.properties") {
            expand("version" to version)
        }
        // Chromium loads the woff2 faces; the woff / ttf fallbacks only add weight.
        from(webBuild) {
            into("web")
            exclude("**/*.ttf", "**/*.woff")
        }
        // The page's Markdown icons as IDE icons, with the New UI dark variants.
        val icons = web.dir("core/ui/luma/markdown")
        from(icons) {
            into("icons/markdown")
        }
        from(icons) {
            into("icons/markdown")
            rename { "${it.removeSuffix(".svg")}_dark.svg" }
            filter { it.replace("#6C707E", "#CED0D6") }
        }
    }

    check {
        dependsOn(webTest)
    }

    runIde {
        // The page's DevTools (chrome://inspect → localhost:9222) and a quiet first start.
        jvmArgumentProviders += CommandLineArgumentProvider {
            listOf(
                "-Dide.browser.jcef.debug.port=9222",
                "-Dide.show.tips.on.startup.default.value=false",
                "-Didea.trust.all.projects=true",
            )
        }
    }
}

// `-PideProject=<dir>` opens a project in the IDE these tasks start.
val ideProject = providers.gradleProperty("ideProject")

tasks.runIde {
    argumentProviders += CommandLineArgumentProvider { listOfNotNull(ideProject.orNull) }
}

// The IDE with the robot server (http://127.0.0.1:8082) for scripted checks of the running plugin.
intellijPlatformTesting.runIde.register("runIdeForUiTests") {
    task {
        jvmArgumentProviders += CommandLineArgumentProvider {
            listOf(
                "-Drobot-server.port=8082",
                "-Dide.mac.message.dialogs.as.sheets=false",
                "-Djb.privacy.policy.text=<!--999.999-->",
                "-Djb.consents.confirmation.enabled=false",
                "-Dide.browser.jcef.debug.port=9222",
                "-Dide.show.tips.on.startup.default.value=false",
                "-Didea.trust.all.projects=true",
            )
        }
        argumentProviders += CommandLineArgumentProvider { listOfNotNull(ideProject.orNull) }
    }
    plugins {
        robotServerPlugin()
    }
}

// `./gradlew runLocalIde -PlocalIdePath=/Applications/IntelliJ\ IDEA.app`: try the plugin in an installed IDE.
providers.gradleProperty("localIdePath").orNull?.let { path ->
    intellijPlatformTesting.runIde.register("runLocalIde") {
        localPath = file(path)
    }
}
