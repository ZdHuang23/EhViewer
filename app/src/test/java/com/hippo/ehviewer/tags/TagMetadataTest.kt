package com.hippo.ehviewer.tags

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TagMetadataTest {
    @Test fun mixedBatchKeepsSuccessAndSkipsMissing() {
        val result = parseTagMetadata("""{"gmetadata":[{"gid":1,"tags":["other:extraneous ads","artist:a:b","other:extraneous ads"]},{"gid":2,"error":"Key missing, or incorrect key provided."}]}""", setOf(1, 2, 3))
        val tags = (result[1] as TagMetadata.Available).tags
        assertEquals(2, tags.size)
        assertTrue(tags.contains(GalleryTag(1, "other", "extraneous ads")))
        assertTrue(tags.contains(GalleryTag(1, "artist", "a:b")))
        assertEquals(TagMetadata.Unavailable, result[2])
        assertEquals(TagMetadata.Failed, result[3])
    }

    @Test fun emptyTagsAreSuccessfulSnapshot() {
        val result = parseTagMetadata("""{"gmetadata":[{"gid":1,"tags":[]}]}""", setOf(1))
        assertEquals(TagMetadata.Available(emptyList()), result[1])
    }

    @Test fun authenticationFailureIsNotPermanentAbsence() {
        val result = parseTagMetadata("""{"gmetadata":[{"gid":1,"error":"Not logged in"}]}""", setOf(1))
        assertEquals(TagMetadata.Failed, result[1])
    }

    @Test fun missingTagsAreNotAnEmptySnapshot() {
        assertEquals(TagMetadata.Failed, parseTagMetadata("""{"gmetadata":[{"gid":1}]}""", setOf(1))[1])
    }

    @Test fun malformedItemDoesNotDiscardOtherGallery() {
        val result = parseTagMetadata("""{"gmetadata":[{"gid":1,"tags":[{}]},{"gid":2,"tags":["other:extraneous ads"]}]}""", setOf(1, 2))
        assertEquals(TagMetadata.Failed, result[1])
        assertEquals(TagMetadata.Available(listOf(GalleryTag(2, "other", "extraneous ads"))), result[2])
    }
}
