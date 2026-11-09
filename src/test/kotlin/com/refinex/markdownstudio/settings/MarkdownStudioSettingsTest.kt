package com.refinex.markdownstudio.settings

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.refinex.markdownstudio.MarkdownStudioBundle
import java.util.Locale

class MarkdownStudioSettingsTest : BasePlatformTestCase() {

    private val settings get() = MarkdownStudioSettings.getInstance()

    override fun tearDown() {
        try {
            settings.loadState(MarkdownStudioSettings.Options())
        } finally {
            super.tearDown()
        }
    }

    fun testAChosenLanguageOverridesTheIdesForMessages() {
        settings.state.language = StudioLanguage.CHINESE
        assertEquals(Locale.SIMPLIFIED_CHINESE, settings.locale)
        assertEquals("字号", MarkdownStudioBundle.message("settings.textSize"))
        settings.state.language = StudioLanguage.ENGLISH
        assertEquals("Text size", MarkdownStudioBundle.message("settings.textSize"))
    }

    fun testBothLanguagesHaveEveryMessage() {
        val keys = { suffix: String ->
            val stream = javaClass.getResourceAsStream("/messages/MarkdownStudioBundle$suffix.properties")!!
            java.util.Properties().apply { stream.use { load(java.io.InputStreamReader(it, Charsets.UTF_8)) } }.keys
        }
        assertEquals(keys(""), keys("_zh_CN"))
    }
}
