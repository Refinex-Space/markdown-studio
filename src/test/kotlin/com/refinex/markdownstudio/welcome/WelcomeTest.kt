package com.refinex.markdownstudio.welcome

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.refinex.markdownstudio.settings.MarkdownStudioSettings
import com.refinex.markdownstudio.settings.StudioLanguage

class WelcomeTest : BasePlatformTestCase() {

    private val settings get() = MarkdownStudioSettings.getInstance().state

    fun testShownOnceAfterInstallation() {
        val before = settings.welcomeVersion
        try {
            settings.welcomeVersion = null
            assertTrue(Welcome.markShown())
            assertNotNull(settings.welcomeVersion)
            assertFalse(Welcome.markShown())
        } finally {
            settings.welcomeVersion = before
        }
    }

    fun testEverythingThePageShowsIsThereInBothLanguages() {
        val before = settings.language
        try {
            for (language in listOf(StudioLanguage.ENGLISH, StudioLanguage.CHINESE)) {
                settings.language = language
                val data = WelcomeData.current()
                for ((key, value) in data.text) assertFalse("$language: welcome.$key is blank", value.isBlank())
                assertEquals(listOf("LIVE", "SOURCE", "SPLIT", "PREVIEW"), data.layouts.map { it.id })
                assertEquals(6, data.features.size)
                for (f in data.features) assertTrue("${f.id} has no icon", f.icon.contains("<svg") && f.icon.contains("currentColor"))
                assertTrue(data.logo.contains("<svg"))
                assertEquals("/", data.keys.first().keys)
                assertTrue("the keymap's Bold shortcut is listed", data.keys.size > 1)
            }
        } finally {
            settings.language = before
        }
    }

    fun testVersionComesFromTheBuild() {
        assertTrue(Welcome.version(), Regex("""\d+\.\d+\.\d+.*""").matches(Welcome.version()))
    }

    fun testOnlyTheWelcomeFileOpensInTheWelcomeEditor() {
        val provider = WelcomeEditorProvider()
        assertTrue(provider.accept(project, WelcomeFile()))
        assertFalse(provider.accept(project, LightVirtualFile("README.md", "# Hi")))
    }

    fun testPageAndToursAreBundled() {
        val index = javaClass.getResourceAsStream("/welcome/index.html")!!.use { it.readBytes().toString(Charsets.UTF_8) }
        assertTrue("the IDE channel goes in here", index.contains("<!--studio-host-->"))
        for (path in listOf("/welcome/welcome.css", "/welcome/welcome.js", "/welcome/tour_en.md", "/welcome/tour_zh.md")) {
            assertNotNull(path, javaClass.getResource(path))
        }
    }

    fun testWelcomeActionIsRegistered() {
        val action = ActionManager.getInstance().getAction("MarkdownStudio.Welcome")
        assertTrue(action is ShowWelcomeAction)
        assertFalse(action.templatePresentation.text.isNullOrBlank())
    }
}
