package com.refinex.markdownstudio.page

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class DocumentAuthorityTest : BasePlatformTestCase() {

    private val sent = ArrayList<IdeMessage>()
    private val carets = ArrayList<Int>()
    private lateinit var authority: DocumentAuthority
    private lateinit var textEditor: TextEditor

    override fun setUp() {
        super.setUp()
        myFixture.configureByText("notes.md", "Hello\n")
        textEditor = TextEditorProvider.getInstance().getTextEditor(myFixture.editor)
        val disposable = Disposer.newDisposable(testRootDisposable)
        authority = DocumentAuthority(project, myFixture.editor.document, disposable, { sent += it }) { carets += it }
    }

    private fun push(version: Int, from: Int, removed: Int, text: String, event: String? = "input.type") =
        PageMessage.Push(
            version,
            listOf(WireUpdate(ChangeSets.replacement(myFixture.editor.document.textLength, from, removed, text), "page")),
            event,
        )

    private fun undo() = UndoManager.getInstance(project).undo(textEditor)

    fun testAcceptsAPushAtTheCurrentVersion() {
        val p = push(0, 5, 0, " world")
        authority.push(p)
        assertEquals("Hello world\n", myFixture.editor.document.text)
        assertEquals(1, authority.version)
        assertEquals(listOf(IdeMessage.Updates(1, p.updates)), sent)
    }

    fun testAnswersAStalePushWithoutApplyingIt() {
        WriteCommandAction.runWriteCommandAction(project) { myFixture.editor.document.insertString(0, ">") }
        sent.clear()
        authority.push(push(0, 0, 0, "x"))
        assertEquals(">Hello\n", myFixture.editor.document.text)
        assertEquals(listOf(IdeMessage.Updates(1, emptyList())), sent)
    }

    fun testSendsChangesMadeInTheIde() {
        WriteCommandAction.runWriteCommandAction(project) { myFixture.editor.document.replaceString(0, 5, "Bye") }
        val update = sent.single() as IdeMessage.Updates
        assertEquals(1, update.version)
        assertEquals(DocumentAuthority.IDE_CLIENT, update.updates.single().clientID)
        assertEquals("""[[5,"Bye"],1]""", update.updates.single().changes.toString())
    }

    fun testTypingIsOneUndoStepAndTheCaretFollowsTheUndo() {
        authority.push(push(0, 5, 0, " "))
        authority.push(push(1, 6, 0, "w"))
        authority.push(push(2, 7, 0, "o"))
        assertEquals("Hello wo\n", myFixture.editor.document.text)
        sent.clear()
        undo()
        assertEquals("Hello\n", myFixture.editor.document.text)
        // The page gets the undo as IDE changes and puts its caret where the text was removed.
        assertTrue(sent.all { it is IdeMessage.Updates && it.updates.all { u -> u.clientID == DocumentAuthority.IDE_CLIENT } })
        assertEquals(5, carets.last())
    }

    fun testAFormattingCommandIsItsOwnUndoStep() {
        authority.push(push(0, 5, 0, "!"))
        authority.push(push(1, 0, 0, "# ", event = "input.format"))
        assertEquals("# Hello!\n", myFixture.editor.document.text)
        undo()
        assertEquals("Hello!\n", myFixture.editor.document.text)
    }

    fun testAPushForAnotherDocumentLengthResetsThePage() {
        val bad = PageMessage.Push(0, listOf(WireUpdate(ChangeSets.replacement(99, 0, 0, "x"), "page")))
        authority.push(bad)
        assertEquals("Hello\n", myFixture.editor.document.text)
        assertEquals(listOf(IdeMessage.Reset("Hello\n", 0)), sent)
    }
}
