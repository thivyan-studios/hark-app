package com.thivyanstudios.hark.util

import org.junit.Assert.assertEquals
import org.junit.Test

class TranscriptFormatterTest {

    @Test
    fun `finished phrase gets sentence case and a full stop`() {
        assertEquals(
            "The brown dog on the floor.",
            TranscriptFormatter.sentence("THE BROWN DOG ON THE FLOOR"),
        )
    }

    @Test
    fun `partial phrase gets sentence case but no full stop`() {
        assertEquals("The brown dog", TranscriptFormatter.partial("THE BROWN DOG"))
    }

    @Test
    fun `pronoun I and its contractions are capitalised`() {
        assertEquals(
            "I think I'm sure I'll go and I've said I'd go.",
            TranscriptFormatter.sentence("I THINK I'M SURE I'LL GO AND I'VE SAID I'D GO"),
        )
    }

    @Test
    fun `letter i inside words is left alone`() {
        assertEquals("It is in the kitchen.", TranscriptFormatter.sentence("IT IS IN THE KITCHEN"))
    }

    @Test
    fun `extra whitespace is collapsed`() {
        assertEquals("Hello there.", TranscriptFormatter.sentence("  HELLO    THERE  "))
    }

    @Test
    fun `blank input stays blank`() {
        assertEquals("", TranscriptFormatter.sentence("   "))
        assertEquals("", TranscriptFormatter.partial(""))
    }

    @Test
    fun `existing end punctuation is not doubled`() {
        assertEquals("Done.", TranscriptFormatter.sentence("DONE."))
    }
}
