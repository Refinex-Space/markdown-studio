package com.refinex.markdownstudio.page

import com.intellij.ide.ui.UISettingsUtils
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors as Syntax
import com.intellij.openapi.editor.colors.CodeInsightColors
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.markup.EffectType
import com.intellij.ui.ColorUtil
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.NamedColorUtil
import com.intellij.util.ui.UIUtil
import com.refinex.markdownstudio.settings.MarkdownStudioSettings
import java.awt.Color
import java.awt.Font
import java.util.Locale
import kotlin.math.roundToInt

/**
 * The IDE's look as the page's design tokens (`web/core/styles/tokens.css`): the page keeps
 * its token set for the light or dark scheme and takes these over it, so it shows the IDE's
 * theme, editor colour scheme and fonts, custom ones included.
 */
object ThemeTokens {

    fun current(): PageTheme {
        val scheme = EditorColorsManager.getInstance().globalScheme
        val tokens = Tokens(scheme.defaultBackground)
        // The page is a document in the editor area: the editor scheme decides light or dark. The
        // IDE's interface colours apply when they agree with it (a light UI may edit on a dark scheme).
        val dark = ColorUtil.isDark(scheme.defaultBackground)
        editor(scheme, tokens)
        syntax(scheme, tokens)
        if (dark == !JBColor.isBright()) ui(tokens)
        fonts(scheme, tokens)
        return PageTheme(if (dark) "dark" else "light", tokens.map)
    }

    /**
     * The IDE zoom (View | Appearance | Zoom IDE): JCEF renders the pages at it, so it is the number of
     * component pixels per page pixel. The IDE reports 0 while it starts; that is 1 here.
     */
    fun zoom(): Double = UISettingsUtils.getInstance().currentIdeScale.toDouble().takeIf { it > 0 && it.isFinite() } ?: 1.0

    /** Token values; translucent colours are laid over the editor background (the page's charts can't blend). */
    private class Tokens(private val background: Color) {
        val map = LinkedHashMap<String, String>()

        fun color(token: String, color: Color?) {
            if (color != null) map[token] = css(if (color.alpha < 255) ColorUtil.mix(background, ColorUtil.withAlpha(color, 1.0), color.alpha / 255.0) else color)
        }

        operator fun set(token: String, value: String) {
            map[token] = value
        }
    }

    private fun editor(scheme: EditorColorsScheme, t: Tokens) {
        val bg = scheme.defaultBackground
        val fg = scheme.defaultForeground
        t.color("--bg-editor", bg)
        t.color("--bg-island", bg)
        t.color("--bg-app", bg)
        t.color("--bg-editor-gutter", scheme.getColor(EditorColors.GUTTER_BACKGROUND) ?: bg)
        t.color("--fg-editor", fg)
        t.color("--caret", scheme.getColor(EditorColors.CARET_COLOR))
        t.color("--bg-current-line", scheme.getColor(EditorColors.CARET_ROW_COLOR))
        val selection = scheme.getColor(EditorColors.SELECTION_BACKGROUND_COLOR)
        t.color("--bg-editor-selection", selection)
        t.color("--bg-editor-selection-inactive", selection?.let { ColorUtil.mix(it, bg, 0.4) })
        t.color("--fg-line-number", scheme.getColor(EditorColors.LINE_NUMBERS_COLOR))
        t.color("--fg-line-number-current", scheme.getColor(EditorColors.LINE_NUMBER_ON_CARET_ROW_COLOR))
        t.color("--bg-search-match", scheme.background(EditorColors.TEXT_SEARCH_RESULT_ATTRIBUTES))
        t.color("--bg-search-match-current", scheme.background(EditorColors.SEARCH_RESULT_ATTRIBUTES))
        t.color("--bg-identifier-under-caret", scheme.background(EditorColors.IDENTIFIER_UNDER_CARET_ATTRIBUTES))
        t.color("--bg-matched-brace", scheme.background(CodeInsightColors.MATCHED_BRACE_ATTRIBUTES))
    }

    private fun syntax(scheme: EditorColorsScheme, t: Tokens) {
        val keys = linkedMapOf(
            "--syn-keyword" to Syntax.KEYWORD,
            "--syn-string" to Syntax.STRING,
            "--syn-number" to Syntax.NUMBER,
            "--syn-comment" to Syntax.LINE_COMMENT,
            "--syn-doc-comment" to Syntax.DOC_COMMENT,
            "--syn-docstring" to Syntax.DOC_COMMENT,
            "--syn-function" to Syntax.FUNCTION_DECLARATION,
            "--syn-function-call" to Syntax.FUNCTION_CALL,
            "--syn-type" to Syntax.CLASS_NAME,
            "--syn-constant" to Syntax.CONSTANT,
            "--syn-property" to Syntax.INSTANCE_FIELD,
            "--syn-annotation" to Syntax.METADATA,
            "--syn-decorator" to Syntax.METADATA,
            "--syn-punct" to Syntax.OPERATION_SIGN,
            "--syn-variable" to Syntax.LOCAL_VARIABLE,
            "--syn-param" to Syntax.PARAMETER,
            "--syn-builtin" to Syntax.PREDEFINED_SYMBOL,
            "--syn-tag" to Syntax.MARKUP_TAG,
            "--syn-attribute" to Syntax.MARKUP_ATTRIBUTE,
            "--syn-escape" to Syntax.VALID_STRING_ESCAPE,
        )
        for ((token, key) in keys) t.color(token, scheme.getAttributes(key)?.foregroundColor)
        val comment = scheme.getAttributes(Syntax.LINE_COMMENT)
        t["--syn-comment-style"] = if (comment != null && comment.fontType and Font.ITALIC != 0) "italic" else "normal"
        val error = scheme.getAttributes(CodeInsightColors.ERRORS_ATTRIBUTES)
        t.color("--syn-invalid", error?.effectColor?.takeIf { error.effectType != EffectType.BOXED } ?: error?.foregroundColor)
    }

    private fun ui(t: Tokens) {
        val label = UIUtil.getLabelForeground()
        t.color("--fg-strong", label)
        t.color("--fg", label)
        t.color("--fg-emphasis", label)
        t.color("--fg-secondary", UIUtil.getContextHelpForeground())
        t.color("--fg-muted", NamedColorUtil.getInactiveTextColor())
        t.color("--fg-subtle", NamedColorUtil.getInactiveTextColor())
        t.color("--fg-faint", UIUtil.getLabelDisabledForeground())
        t.color("--bg-elev", UIUtil.getPanelBackground())
        t.color("--bg-popup", JBUI.CurrentTheme.Popup.BACKGROUND)
        t.color("--border-popup", JBUI.CurrentTheme.Popup.borderColor(true))
        t.color("--border", JBColor.border())
        t.color("--border-strong", JBColor.namedColor("Component.borderColor", JBColor.border()))
        t.color("--bg-hover", JBUI.CurrentTheme.ActionButton.hoverBackground())
        t.color("--bg-toolbar-hover", JBUI.CurrentTheme.ActionButton.hoverBackground())
        t.color("--bg-toolbar-pressed", JBUI.CurrentTheme.ActionButton.pressedBackground())
        val selected = UIUtil.getListSelectionBackground(true)
        t.color("--bg-selected", selected)
        t.color("--bg-list-selected", selected)
        t.color("--bg-tree-selected", selected)
        val accent = JBUI.CurrentTheme.Focus.focusColor()
        t.color("--accent", JBColor.namedColor("Button.default.startBackground", accent))
        t.color("--focus-ring", accent)
        t.color("--link", JBUI.CurrentTheme.Link.Foreground.ENABLED)
        t.color("--link-hover", JBUI.CurrentTheme.Link.Foreground.HOVERED)
        t.color("--bg-tooltip", JBUI.CurrentTheme.Tooltip.background())
        t.color("--fg-tooltip", JBUI.CurrentTheme.Tooltip.foreground())
        t.color("--border-tooltip", JBUI.CurrentTheme.Tooltip.borderColor())
        t.color("--bg-input", UIUtil.getTextFieldBackground())
        t.color("--bg-floating-toolbar", JBColor.namedColor("Toolbar.Floating.background", JBUI.CurrentTheme.Popup.BACKGROUND))
    }

    private fun fonts(scheme: EditorColorsScheme, t: Tokens) {
        val zoom = UISettingsUtils.getInstance().currentIdeScale.takeIf { it > 0f } ?: 1f
        val ui = UIUtil.getLabelFont()
        t["--font-ui"] = "${quote(ui.family)}, \"Inter Variable\", Inter, system-ui, -apple-system, sans-serif"
        t["--fs-ui"] = px(ui.size2D / zoom)
        val mono = "${quote(scheme.editorFontName)}, \"JetBrains Mono Variable\", \"JetBrains Mono\", ui-monospace, Menlo, monospace"
        t["--font-mono"] = mono
        t["--luma-editor-font"] = mono
        val size = scheme.editorFontSize2D
        t["--fs-code"] = px(size)
        t["--luma-editor-base-size"] = px(size)
        t["--luma-editor-font-size"] = px(size)
        val line = px((size * LINE_HEIGHT * scheme.lineSpacing).roundToInt().toFloat())
        t["--lh-code"] = line
        t["--luma-editor-base-line"] = line
        t["--luma-editor-line-height"] = line
        t["--studio-code-ligatures"] = if (scheme.fontPreferences.useLigatures()) "normal" else "none"
        document(size, t)
    }

    /** Settings | Tools | Markdown Studio | Appearance: the column's width and the text size. */
    private fun document(editorSize: Float, t: Tokens) {
        val o = MarkdownStudioSettings.getInstance().state
        t["--md-page-width"] = o.readingWidth.px?.let { "${it}px" } ?: "none"
        val text = textSize(editorSize)
        if (!o.followEditorFont) {
            t["--luma-editor-font-size"] = px(text - 1)
            t["--luma-editor-base-size"] = px(text - 1)
        }
        t["--markdown-font-zoom"] = px(text - PREVIEW_TEXT)
    }

    /** The document's text size in CSS pixels: Live Preview's is the editor font size + 1px, or the size chosen in the settings. */
    fun textSize(editorSize: Float = EditorColorsManager.getInstance().globalScheme.editorFontSize2D): Float {
        val o = MarkdownStudioSettings.getInstance().state
        return if (o.followEditorFont) editorSize + 1 else o.fontSize.toFloat()
    }

    private const val PREVIEW_TEXT = 14.5f

    /** Ascent plus descent of a typical monospace face, per em (the IDE multiplies it by the line spacing). */
    private const val LINE_HEIGHT = 1.32f

    private fun EditorColorsScheme.background(key: TextAttributesKey): Color? = getAttributes(key)?.backgroundColor

    fun css(color: Color): String =
        if (color.alpha == 255) "#%02x%02x%02x".format(color.red, color.green, color.blue)
        else "rgba(${color.red}, ${color.green}, ${color.blue}, ${"%.3f".format(Locale.ROOT, color.alpha / 255.0)})"

    private fun px(size: Float): String = "${"%.1f".format(Locale.ROOT, size).removeSuffix(".0")}px"

    private fun quote(family: String): String = "\"${family.replace("\"", "")}\""
}
