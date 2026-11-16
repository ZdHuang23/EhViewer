package com.hippo.ehviewer.tags

/** Cursor-aware token boundaries, including spaces inside quoted tag names. */
object TagCompletion {
    fun start(text: CharSequence, cursor: Int): Int {
        var quoted = false
        var start = 0
        for (i in 0 until cursor.coerceIn(0, text.length)) {
            if (text[i] == '"') quoted = !quoted
            if (!quoted && text[i].isWhitespace()) start = i + 1
        }
        return start
    }

    fun end(text: CharSequence, start: Int): Int {
        var quoted = false
        for (i in start until text.length) {
            if (text[i] == '"') quoted = !quoted
            if (!quoted && text[i].isWhitespace()) return i
        }
        return text.length
    }

    fun replacement(raw: String, operator: String): String {
        if (raw.endsWith(':')) return operator + raw
        val namespace = raw.substringBefore(':', "")
        val name = raw.substringAfter(':')
        return operator + (if (namespace.isEmpty()) "" else "$namespace:") +
            if (name.contains(' ')) "\"$name$\"" else "$name$"
    }
}
