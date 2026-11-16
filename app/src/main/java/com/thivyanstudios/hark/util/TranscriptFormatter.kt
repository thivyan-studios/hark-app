package com.thivyanstudios.hark.util

import java.util.Locale

/**
 * Turns Sherpa-ONNX output ("I HAD A CUP OF COFFEE THIS MORNING") into readable text
 * ("I had a cup of coffee this morning."). The Zipformer model outputs upper-case words with
 * no punctuation, so this applies sentence casing, capitalises "I" (including I'm, I'll, I've,
 * I'd) and ends each finished phrase with a full stop.
 *
 * Names and places can't be detected from the model's output, so they stay lower-case.
 */
object TranscriptFormatter {

    private val whitespace = Regex("\\s+")
    private val pronounI = Regex("\\bi\\b")

    /** A finished phrase (Sherpa detected the speaker paused): cased and ended with a full stop. */
    fun sentence(raw: String): String {
        val text = format(raw)
        if (text.isEmpty()) return text
        return if (text.last() in ".?!") text else "$text."
    }

    /** Words still being spoken: cased, but no full stop yet because the phrase may continue. */
    fun partial(raw: String): String = format(raw)

    private fun format(raw: String): String {
        val text = raw.trim().replace(whitespace, " ").lowercase(Locale.ENGLISH)
        if (text.isEmpty()) return text
        return text
            .replace(pronounI, "I")
            .replaceFirstChar { it.titlecase(Locale.ENGLISH) }
    }
}
