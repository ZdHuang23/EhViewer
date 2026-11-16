package com.hippo.ehviewer.tags

import android.content.Context
import android.util.AttributeSet
import android.widget.MultiAutoCompleteTextView

class TagAutoCompleteTextView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : MultiAutoCompleteTextView(context, attrs) {
    init {
        threshold = 1
        setTokenizer(object : Tokenizer {
            override fun findTokenStart(text: CharSequence, cursor: Int): Int = TagCompletion.start(text, cursor)
            override fun findTokenEnd(text: CharSequence, cursor: Int): Int = TagCompletion.end(text, TagCompletion.start(text, cursor))
            override fun terminateToken(text: CharSequence): CharSequence = text
        })
    }

    override fun replaceText(replacement: CharSequence) {
        clearComposingText()
        val start = TagCompletion.start(text, selectionEnd)
        val end = TagCompletion.end(text, start)
        val suffix = if (end == text.length && !replacement.endsWith(':')) " " else ""
        text.replace(start, end, replacement.toString() + suffix)
        setSelection(start + replacement.length + suffix.length)
    }
}
