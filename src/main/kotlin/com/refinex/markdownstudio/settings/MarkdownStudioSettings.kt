package com.refinex.markdownstudio.settings

import com.intellij.DynamicBundle
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.util.messages.Topic
import com.refinex.markdownstudio.editor.EditorLayout
import java.util.Locale

/** The language of Markdown Studio: the IDE's, or one chosen in its settings. */
enum class StudioLanguage(val locale: Locale?) {
    IDE(null),
    ENGLISH(Locale.ENGLISH),
    CHINESE(Locale.SIMPLIFIED_CHINESE);

    /** The locale in effect: the IDE's language when it is Chinese, else English. */
    val effectiveLocale: Locale
        get() = locale ?: if (DynamicBundle.getLocale().language == Locale.CHINESE.language) Locale.SIMPLIFIED_CHINESE else Locale.ENGLISH
}

/** How wide the document column may grow (the page's `--md-page-width`). */
enum class ReadingWidth(val px: Int?) {
    NARROW(720),
    MEDIUM(900),
    WIDE(1160),
    FULL(null),
}

@Service(Service.Level.APP)
@State(name = "MarkdownStudioSettings", storages = [Storage("markdown-studio.xml")])
class MarkdownStudioSettings : SimplePersistentStateComponent<MarkdownStudioSettings.Options>(Options()) {

    class Options : BaseState() {
        /** Markdown files open in Markdown Studio (otherwise in the IDE's own editors). */
        var enabled by property(true)

        /** The layout of files opened for the first time; `null` means the last one chosen. */
        var defaultLayout by enum<EditorLayout>()

        var lastLayout by enum(EditorLayout.LIVE)

        var language by enum(StudioLanguage.IDE)

        /** In Source and Preview, the preview follows the source editor's scrolling. */
        var syncScrolling by property(true)

        var readingWidth by enum(ReadingWidth.WIDE)

        /** The document's text size follows the editor font; otherwise [fontSize]. */
        var followEditorFont by property(true)

        var fontSize by property(15)

        /** The outline beside the document, one setting for every file. */
        var showOutline by property(true)

        /** The formatting toolbar over selected text in Live Preview. */
        var selectionToolbar by property(true)

        /** Web images (README badges) load in the page, as in the IDE's own Markdown preview. */
        var loadRemoteImages by property(true)

        /** The plugin version the welcome page was shown for; null until it has been (a new installation). */
        var welcomeVersion by string()
    }

    /** The layout a file opened for the first time gets. */
    val initialLayout: EditorLayout
        get() = state.defaultLayout ?: state.lastLayout

    val locale: Locale
        get() = state.language.effectiveLocale

    fun layoutChosen(layout: EditorLayout) {
        state.lastLayout = layout
    }

    /** Tells open editors that settings changed (after Settings | Tools | Markdown Studio is applied). */
    fun changed() {
        ApplicationManager.getApplication().messageBus.syncPublisher(TOPIC).settingsChanged()
    }

    fun interface Listener {
        fun settingsChanged()
    }

    companion object {
        @JvmField
        @Topic.AppLevel
        val TOPIC: Topic<Listener> = Topic(Listener::class.java, Topic.BroadcastDirection.NONE)

        const val MIN_FONT_SIZE = 10
        const val MAX_FONT_SIZE = 32

        fun getInstance(): MarkdownStudioSettings = service()
    }
}
