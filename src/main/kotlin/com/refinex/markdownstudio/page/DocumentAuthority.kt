package com.refinex.markdownstudio.page

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.command.UndoConfirmationPolicy
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.refinex.markdownstudio.MarkdownStudioBundle

/**
 * The authority side of @codemirror/collab over an IntelliJ [Document]: every change has a
 * version; the page pushes its changes with the version it has seen and gets them applied as
 * IDE commands (so the IDE's undo, local history and VCS see them), and every other change of
 * the document (the Source editor, undo, reformat, VCS) goes to the page as an update.
 *
 * Runs on the EDT.
 */
class DocumentAuthority(
    private val project: Project,
    val document: Document,
    parent: Disposable,
    private val send: (IdeMessage) -> Unit,
    /** After an undo or redo: where the page should put its caret. */
    private val onUndoRedo: (offset: Int) -> Unit = {},
) : DocumentListener {

    var version: Int = 0
        private set

    private var applying = false
    private val typing = TypingGroups()

    init {
        document.addDocumentListener(this, parent)
    }

    override fun documentChanged(event: DocumentEvent) {
        if (applying) return
        version++
        send(IdeMessage.Updates(version, listOf(WireUpdate(ChangeSets.fromEvent(event), IDE_CLIENT))))
        if (UndoManager.getInstance(project).isUndoOrRedoInProgress) onUndoRedo(event.offset + event.newLength)
    }

    /** Applies a push of the page, or answers that it was based on an outdated version. */
    fun push(push: PageMessage.Push) {
        if (push.version != version || push.updates.isEmpty()) {
            // The page gets the updates it lacks (sent before this answer), rebases and pushes again.
            send(IdeMessage.Updates(version, emptyList()))
            return
        }
        if (!FileDocumentManager.getInstance().requestWriting(document, project)) {
            send(IdeMessage.Reset(document.text, version))
            return
        }
        val expected = push.updates.fold(document.textLength) { length, u ->
            if (ChangeSets.lengthBefore(u.changes) != length) -1 else ChangeSets.lengthAfter(u.changes)
        }
        if (expected < 0) {
            thisLogger().warn("Markdown Studio: push does not match the document; resetting the page")
            send(IdeMessage.Reset(document.text, version))
            return
        }
        applying = true
        try {
            CommandProcessor.getInstance().executeCommand(
                project,
                { ApplicationManager.getApplication().runWriteAction { push.updates.forEach { ChangeSets.apply(document, it.changes) } } },
                MarkdownStudioBundle.message("command.edit"),
                typing.groupFor(push),
                UndoConfirmationPolicy.DEFAULT,
                document,
            )
        } finally {
            applying = false
        }
        if (document.textLength != expected) {
            // Something else changed the document while the push applied: the page starts over from it.
            version += push.updates.size
            send(IdeMessage.Reset(document.text, version))
            return
        }
        version += push.updates.size
        send(IdeMessage.Updates(version, push.updates))
    }

    /** The document as the page should start from. */
    fun snapshot(): Pair<String, Int> = document.text to version

    companion object {
        const val IDE_CLIENT = "ide"
    }
}

/**
 * Undo granularity: consecutive typing (or deleting) in one place is one undoable step, as in
 * the IDE's editor; anything else (a paste, a formatting command, a new place) starts another.
 */
internal class TypingGroups(private val clock: () -> Long = System::currentTimeMillis) {
    private var group = Any()
    private var kind: String? = null
    private var at = 0L

    fun groupFor(push: PageMessage.Push): Any {
        val now = clock()
        val k = when {
            push.userEvent == null -> null
            push.userEvent.startsWith("input.type") -> "type"
            push.userEvent.startsWith("delete") -> "delete"
            else -> null
        }
        if (k == null || k != kind || now - at > MERGE_WINDOW_MS) group = Any()
        kind = k
        at = now
        return group
    }

    private companion object {
        const val MERGE_WINDOW_MS = 2_000L
    }
}
