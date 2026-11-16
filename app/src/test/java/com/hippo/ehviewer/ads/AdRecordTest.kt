package com.hippo.ehviewer.ads

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdRecordTest {
    @Test fun decisionsRoundTripAndRestoreIsProtected() {
        val original = AdRecord(123, "token", 20).mark(19).restore(19).mark(18).copy(manualComplete = true)
        assertEquals(original, AdRecord.decode(original.encode()))
        assertFalse(19 in original.ads)
        assertTrue(19 in original.restored)
        assertFalse(19 in original.mark(19).restored)
        assertTrue(original.matches(123, "token", 20))
        assertFalse(original.matches(123, "new-token", 20))
        assertFalse(original.matches(123, "token", 21))
    }

    @Test fun tailNeverIncludesEarlierPagesOrNegativeIndices() {
        assertEquals((13..19).toList(), AdRecord.tail(20).toList())
        assertEquals((0..3).toList(), AdRecord.tail(4).toList())
    }

    @Test fun corruptOrFutureRecordsAreRejected() {
        val encoded = AdRecord(1, "t", 2).mark(1).encode()
        listOf(encoded.replace("\"version\":1", "\"version\":2"), encoded.replace("\"ads\":[1]", "\"ads\":[2]"), "broken").forEach {
            assertTrue(runCatching { AdRecord.decode(it) }.isFailure)
        }
    }
}
