package com.refinex.markdownstudio.welcome

import com.intellij.ide.DataManager
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.scratch.ScratchFileService
import com.intellij.ide.scratch.ScratchRootType
import com.intellij.lang.Language
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.fileTypes.PlainTextLanguage
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.options.ex.Settings
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.wm.IdeFocusManager
import com.refinex.markdownstudio.MarkdownStudioBundle
import com.refinex.markdownstudio.actions.localizePresentation
import com.refinex.markdownstudio.editor.EditorLayout
import com.refinex.markdownstudio.editor.MarkdownStudioEditor
import com.refinex.markdownstudio.settings.MarkdownStudioConfigurable
import com.refinex.markdownstudio.settings.MarkdownStudioEditingConfigurable
import com.refinex.markdownstudio.settings.MarkdownStudioSettings
import java.awt.Window
import java.util.Locale
import java.util.Properties
import javax.swing.JDialog
import javax.swing.Timer

/** The welcome tab: shown once after the plugin is installed, and from Help | Markdown Studio Welcome. */
object Welcome {
    const val PLUGIN_ID = "com.refinex.markdownstudio"

    /** The plugin's version, written into `welcome/build.properties` by the build. */
    fun version(): String = Welcome::class.java.getResourceAsStream("/welcome/build.properties")
        ?.use { Properties().apply { load(it) }.getProperty("version") }
        ?.takeUnless { it.startsWith("$") }
        .orEmpty()

    /** Opens the welcome tab, or brings the open one forward. */
    fun open(project: Project) {
        val file = FileEditorManager.getInstance(project).openFiles.firstOrNull { it is WelcomeFile } ?: WelcomeFile()
        OpenFileDescriptor(project, file).navigate(true)
    }

    /** A new installation (no welcome shown yet): the welcome tab, once. */
    fun showOnce(project: Project) {
        val app = ApplicationManager.getApplication()
        if (app.isUnitTestMode || app.isHeadlessEnvironment || project.isDisposed) return
        if (!markShown()) return
        open(project)
    }

    /** Records that the welcome page has been shown; false when it already had been. */
    internal fun markShown(): Boolean {
        val state = MarkdownStudioSettings.getInstance().state
        if (state.welcomeVersion != null) return false
        state.welcomeVersion = version().ifEmpty { "?" }
        return true
    }

    /** The tour: a Markdown scratch file in the settings' language, opened in Live Preview. */
    fun openTour(project: Project) {
        val chinese = MarkdownStudioSettings.getInstance().locale.language == Locale.CHINESE.language
        val text = Welcome::class.java.getResourceAsStream(if (chinese) "/welcome/tour_zh.md" else "/welcome/tour_en.md")
            ?.use { it.readBytes().toString(Charsets.UTF_8) } ?: return
        val language = Language.findLanguageByID("Markdown") ?: PlainTextLanguage.INSTANCE
        val file = ScratchRootType.getInstance().createScratchFile(
            project,
            MarkdownStudioBundle.message("welcome.tour.file"),
            language,
            text,
            ScratchFileService.Option.create_if_missing,
        ) ?: return
        OpenFileDescriptor(project, file).navigate(true)
        FileEditorManager.getInstance(project).getAllEditors(file)
            .filterIsInstance<MarkdownStudioEditor>()
            .firstOrNull()
            ?.setEditorLayout(EditorLayout.LIVE)
    }

    fun openSettings(project: Project) {
        ShowSettingsUtil.getInstance().showSettingsDialog(project, MarkdownStudioConfigurable::class.java)
    }

    /**
     * Settings | Keymap, filtered to the plugin's actions. The filter goes through the open dialog's
     * [Settings] (as in Settings | Tools | Markdown Studio | Editing) once the keymap page shows:
     * searching earlier, while the page still loads its keymaps, fails inside the platform.
     */
    fun openKeymap(project: Project) {
        ShowSettingsUtil.getInstance().showSettingsDialog(
            project,
            { c: Configurable -> c is SearchableConfigurable && c.id == MarkdownStudioEditingConfigurable.KEYMAP_ID },
            { filterWhenShown() },
        )
    }

    /**
     * Waits (up to 4 s) for the keymap page of the dialog being opened, then filters it to the plugin;
     * once more half a second later, as the page's first set-up after showing clears the search.
     */
    private fun filterWhenShown() {
        var ticks = 0
        var shownAt = -1
        val timer = Timer(100, null)
        timer.addActionListener {
            if (++ticks > 40) return@addActionListener timer.stop()
            // The dialog's Settings, found through its window: focus may still be in the page that opened it.
            val settings = Window.getWindows().asSequence()
                .filter { it.isShowing && it is JDialog }
                .mapNotNull { (it as JDialog).contentPane.components.firstOrNull() }
                .firstNotNullOfOrNull { Settings.KEY.getData(DataManager.getInstance().getDataContext(it)) }
                ?: return@addActionListener
            val keymap = settings.find(MarkdownStudioEditingConfigurable.KEYMAP_ID) ?: return@addActionListener
            if (keymap.createComponent()?.isShowing != true) return@addActionListener
            if (shownAt < 0) shownAt = ticks
            if (ticks == shownAt || ticks == shownAt + 5) settings.select(keymap, MarkdownStudioEditingConfigurable.KEYMAP_FILTER)
            if (ticks >= shownAt + 5) timer.stop()
        }
        timer.start()
    }
}

/** Opening a project after the plugin was installed (with a restart). */
class WelcomeStartup : ProjectActivity {
    override suspend fun execute(project: Project) {
        if (MarkdownStudioSettings.getInstance().state.welcomeVersion != null) return
        ApplicationManager.getApplication().invokeLater({ Welcome.showOnce(project) }, ModalityState.nonModal(), project.disposed)
    }
}

/** The plugin installed without a restart (see `WelcomeInstallListener`): the welcome tab in the project in use. */
internal fun welcomeAfterInstall(pluginDescriptor: IdeaPluginDescriptor) {
    if (pluginDescriptor.pluginId.idString != Welcome.PLUGIN_ID) return
    val project = IdeFocusManager.getGlobalInstance().lastFocusedFrame?.project
        ?: ProjectManager.getInstance().openProjects.firstOrNull()
        ?: return
    ApplicationManager.getApplication().invokeLater({ Welcome.showOnce(project) }, ModalityState.nonModal(), project.disposed)
}

class ShowWelcomeAction : DumbAwareAction() {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = e.project != null
        localizePresentation(e)
    }

    override fun actionPerformed(e: AnActionEvent) {
        e.project?.let(Welcome::open)
    }
}
