package com.toolx.app.core

import org.junit.Assert.assertEquals
import org.junit.Test

class RecentsTest {

    @Test fun newestFirstWithoutDuplicates() {
        var r = emptyList<String>()
        r = AppSettings.pushRecent(r, "trim")
        r = AppSettings.pushRecent(r, "merge")
        r = AppSettings.pushRecent(r, "trim") // opened again: moves back to the front
        assertEquals(listOf("trim", "merge"), r)
    }

    @Test fun cappedAtMax() {
        var r = emptyList<String>()
        (1..20).forEach { r = AppSettings.pushRecent(r, "t$it", max = 12) }
        assertEquals(12, r.size)
        assertEquals("t20", r.first())
        assertEquals("t9", r.last()) // t1..t8 dropped, oldest first
    }
}
