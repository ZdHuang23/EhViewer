package com.hippo.ehviewer.tags

import org.junit.Assert.assertEquals
import org.junit.Test

class TagCompletionTest {
    @Test fun quotedSpacesRemainInOneToken() {
        val text = "l:chinese$ -o:\"extraneous ad\" ~m:muscle$"
        val cursor = text.indexOf("ad") + 2
        val start = TagCompletion.start(text, cursor)
        assertEquals("-o:\"extraneous ad\"", text.substring(start, TagCompletion.end(text, start)))
        assertEquals(0, TagCompletion.start("o:ads", 0))
    }

    @Test fun completionPreservesOperatorAndQuotesMultiwordTags() {
        assertEquals("-o:\"extraneous ads$\"", TagCompletion.replacement("o:extraneous ads", "-"))
        assertEquals("~l:chinese$", TagCompletion.replacement("l:chinese", "~"))
        assertEquals("f:", TagCompletion.replacement("f:", ""))
    }
}
