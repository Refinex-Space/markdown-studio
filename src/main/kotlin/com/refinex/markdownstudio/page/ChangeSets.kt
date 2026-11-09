package com.refinex.markdownstudio.page

import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.event.DocumentEvent
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

/**
 * CodeMirror `ChangeSet` JSON (`ChangeSet.toJSON()`): a number keeps that many characters, an
 * array `[length, ...lines]` replaces `length` characters by the lines joined with `\n` (just
 * `[length]` deletes). Offsets are UTF-16 units, as in IntelliJ documents.
 */
object ChangeSets {

    /** The change of `event` as a change set over the document before it. */
    fun fromEvent(event: DocumentEvent): JsonArray {
        val before = event.document.textLength - event.newLength + event.oldLength
        return replacement(before, event.offset, event.oldLength, event.newFragment)
    }

    fun replacement(length: Int, offset: Int, removed: Int, inserted: CharSequence): JsonArray = buildJsonArray {
        if (offset > 0) add(offset)
        add(buildJsonArray {
            add(removed)
            if (inserted.isNotEmpty()) inserted.split('\n').forEach { add(it) }
        })
        val rest = length - offset - removed
        if (rest > 0) add(rest)
    }

    /** The document length a change set applies to. */
    fun lengthBefore(changes: JsonArray): Int = changes.sumOf { part ->
        if (part is JsonPrimitive) part.int else part.jsonArray[0].jsonPrimitive.int
    }

    /** The document length after a change set. */
    fun lengthAfter(changes: JsonArray): Int = changes.sumOf { part ->
        if (part is JsonPrimitive) part.int
        else part.jsonArray.let { arr ->
            if (arr.size == 1) 0 else (1 until arr.size).sumOf { arr[it].jsonPrimitive.content.length } + arr.size - 2
        }
    }

    /** Applies `changes` (over the current text of `document`); needs a write action. */
    fun apply(document: Document, changes: JsonArray) {
        val expected = lengthBefore(changes)
        require(expected == document.textLength) {
            "change set for length $expected, document has ${document.textLength}"
        }
        val edits = ArrayList<Edit>()
        var pos = 0
        for (part in changes) {
            if (part is JsonPrimitive) {
                pos += part.int
                continue
            }
            val arr = part.jsonArray
            val length = arr[0].jsonPrimitive.int
            val text = if (arr.size == 1) "" else (1 until arr.size).joinToString("\n") { arr[it].jsonPrimitive.content }
            edits += Edit(pos, pos + length, text)
            pos += length
        }
        // From the end, so the offsets of the earlier edits still hold.
        for (edit in edits.asReversed()) document.replaceString(edit.from, edit.to, edit.text)
    }

    private data class Edit(val from: Int, val to: Int, val text: String)
}
