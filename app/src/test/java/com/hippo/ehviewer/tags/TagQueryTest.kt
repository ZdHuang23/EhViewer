package com.hippo.ehviewer.tags

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TagQueryTest {
    private val tags = listOf(GalleryTag(1, "language", "chinese"), GalleryTag(1, "other", "extraneous ads"))

    @Test fun shortAndFullNamespacesAreEquivalent() {
        assertTrue(TagQuery.parse("o:\"extraneous ads$\" l:chinese$").matches(tags))
        assertTrue(TagQuery.parse("other:extraneous_ads$ language:chinese$").matches(tags))
    }

    @Test fun requiredOrAndExclusionFollowEhSyntax() {
        assertTrue(TagQuery.parse("o:extraneous ~l:chinese$ ~l:japanese$").matches(tags))
        assertFalse(TagQuery.parse("~l:chinese$ ~l:japanese$ -o:\"extraneous ads$\"").matches(tags))
        assertFalse(TagQuery.parse("l:chinese$ l:japanese$").matches(tags))
    }

    @Test fun exactSuffixPreventsPrefixMatches() {
        assertTrue(TagQuery.parse("o:extraneous").matches(tags))
        assertFalse(TagQuery.parse("o:extraneous$").matches(tags))
        assertTrue(TagQuery.parse("tag:\"extraneous ads$\"").matches(tags))
    }

    @Test(expected = IllegalArgumentException::class)
    fun semicolonIsRejected() {
        TagQuery.parse("l:chinese$;l:japanese$")
    }

    @Test(expected = IllegalArgumentException::class)
    fun unmatchedQuoteIsRejected() {
        TagQuery.parse("o:\"extraneous ads")
    }

    @Test(expected = IllegalArgumentException::class)
    fun unsupportedQualifierIsRejected() {
        TagQuery.parse("uploader:someone")
    }
}
