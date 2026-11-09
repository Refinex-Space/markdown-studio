package com.refinex.markdownstudio.page

import com.intellij.ide.ui.UISettings
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.awt.Color

class ThemeTokensTest : BasePlatformTestCase() {

    fun testTokensCoverTheEditorAndFonts() {
        val theme = ThemeTokens.current()
        assertTrue(theme.scheme == "light" || theme.scheme == "dark")
        for (token in listOf("--bg-editor", "--fg-editor", "--syn-keyword", "--font-ui", "--luma-editor-font-size", "--md-page-width"))
            assertTrue("missing $token", theme.tokens.containsKey(token))
        assertTrue(theme.tokens["--luma-editor-font-size"]!!.endsWith("px"))
        // Opaque colours only: the page's diagrams cannot blend translucent ones.
        assertFalse(theme.tokens.values.any { it.startsWith("rgba(") })
    }

    /** While the IDE starts it reports a scale of 0; page coordinates must not collapse to 0 then. */
    fun testZoomIsOneWhileTheIdeReportsNoScale() {
        val settings = UISettings.getInstance()
        val before = settings.ideScale
        try {
            settings.ideScale = 0f
            assertEquals(1.0, ThemeTokens.zoom())
            settings.ideScale = 1.25f
            assertEquals(1.25, ThemeTokens.zoom(), 1e-6)
        } finally {
            settings.ideScale = before
        }
    }

    fun testCssColors() {
        assertEquals("#0a0b0c", ThemeTokens.css(Color(10, 11, 12)))
        assertEquals("rgba(1, 2, 3, 0.502)", ThemeTokens.css(Color(1, 2, 3, 128)))
    }
}
