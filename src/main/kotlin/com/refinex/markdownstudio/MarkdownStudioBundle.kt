package com.refinex.markdownstudio

import com.intellij.AbstractBundle
import com.intellij.DynamicBundle
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.PropertyKey
import com.refinex.markdownstudio.settings.MarkdownStudioSettings
import java.util.Locale
import java.util.ResourceBundle
import java.util.concurrent.ConcurrentHashMap

@NonNls
private const val BUNDLE = "messages.MarkdownStudioBundle"

/**
 * Markdown Studio's messages, in the IDE's language or the one chosen in
 * Settings | Tools | Markdown Studio (English or Simplified Chinese).
 */
object MarkdownStudioBundle : DynamicBundle(MarkdownStudioBundle::class.java, BUNDLE) {

    private val chosen = ConcurrentHashMap<Locale, ResourceBundle>()

    @JvmStatic
    @Nls
    fun message(@PropertyKey(resourceBundle = BUNDLE) key: String, vararg params: Any): String {
        val locale = runCatching { MarkdownStudioSettings.getInstance().state.language.locale }.getOrNull()
            ?: return getMessage(key, *params)
        val bundle = chosen.computeIfAbsent(locale) {
            ResourceBundle.getBundle(
                BUNDLE,
                it,
                MarkdownStudioBundle::class.java.classLoader,
                ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES),
            )
        }
        return AbstractBundle.message(bundle, key, *params)
    }
}
