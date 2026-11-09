package com.refinex.markdownstudio.settings

import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.MutableProperty
import com.intellij.ui.dsl.builder.RightGap
import com.intellij.ui.dsl.builder.bind
import com.intellij.ui.dsl.builder.bindIntValue
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.selected
import com.refinex.markdownstudio.MarkdownStudioBundle.message
import kotlin.math.roundToInt

/** Settings | Tools | Markdown Studio | Appearance: the document's width and text size, the outline, images. */
class MarkdownStudioAppearanceConfigurable : StudioConfigurable("settings.appearance.displayName", ID) {

    override fun createPanel(): DialogPanel = panel {
        val o = settings.state
        group(message("settings.group.document")) {
            row(message("settings.readingWidth")) {
                segmentedButton(ReadingWidth.entries) { text = widthName(it) }
                    .bind(MutableProperty({ o.readingWidth }, { o.readingWidth = it ?: ReadingWidth.WIDE }))
            }
            buttonsGroup(message("settings.textSize")) {
                row {
                    val size = EditorColorsManager.getInstance().globalScheme.editorFontSize2D.roundToInt() + 1
                    radioButton(message("settings.textSize.editor", size), true)
                }
                row {
                    val custom = radioButton(message("settings.textSize.custom"), false)
                    spinner(MarkdownStudioSettings.MIN_FONT_SIZE..MarkdownStudioSettings.MAX_FONT_SIZE)
                        .bindIntValue(o::fontSize)
                        .enabledIf(custom.selected)
                        .gap(RightGap.SMALL)
                    label(message("settings.textSize.unit"))
                }
            }.bind(o::followEditorFont)
            row {
                checkBox(message("settings.showOutline")).bindSelected(o::showOutline)
            }
        }
        group(message("settings.group.images")) {
            row {
                checkBox(message("settings.remoteImages")).bindSelected(o::loadRemoteImages)
                contextHelp(message("settings.remoteImages.help"))
            }
        }
    }

    companion object {
        const val ID = "com.refinex.markdownstudio.settings.appearance"

        fun widthName(width: ReadingWidth): String = when (width) {
            ReadingWidth.NARROW -> message("settings.readingWidth.narrow")
            ReadingWidth.MEDIUM -> message("settings.readingWidth.medium")
            ReadingWidth.WIDE -> message("settings.readingWidth.wide")
            ReadingWidth.FULL -> message("settings.readingWidth.full")
        }
    }
}
