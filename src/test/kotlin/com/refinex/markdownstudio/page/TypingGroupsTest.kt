package com.refinex.markdownstudio.page

import junit.framework.TestCase

class TypingGroupsTest : TestCase() {

    private var now = 0L
    private val groups = TypingGroups { now }

    private fun push(event: String?) = PageMessage.Push(0, emptyList(), event)

    fun testConsecutiveTypingSharesAGroup() {
        val a = groups.groupFor(push("input.type"))
        now += 300
        assertSame(a, groups.groupFor(push("input.type.compose")))
    }

    fun testAPauseOrAnotherKindStartsANewGroup() {
        val a = groups.groupFor(push("input.type"))
        now += 5_000
        val b = groups.groupFor(push("input.type"))
        assertNotSame(a, b)
        val c = groups.groupFor(push("delete.backward"))
        assertNotSame(b, c)
        assertNotSame(c, groups.groupFor(push("input.paste")))
        assertNotSame(groups.groupFor(push(null)), groups.groupFor(push(null)))
    }
}
