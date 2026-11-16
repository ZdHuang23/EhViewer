package com.hippo.ehviewer.tags

/** Local tag subset of EH search syntax. Unknown metadata must be excluded by the caller. */
class TagQuery private constructor(private val terms: List<Term>) {
    private data class Term(val namespace: String?, val name: String, val exact: Boolean, val mode: Char) {
        private val prefixPattern = Regex("(?:^| )${Regex.escape(name)}", RegexOption.IGNORE_CASE)
        fun matches(tag: GalleryTag): Boolean = (namespace == null || namespace == tag.namespace) &&
            if (exact) tag.name.equals(name, true) else prefixPattern.containsMatchIn(tag.name)
    }

    fun matches(tags: List<GalleryTag>): Boolean {
        val required = terms.filter { it.mode == '+' }
        val alternatives = terms.filter { it.mode == '~' }
        val excluded = terms.filter { it.mode == '-' }
        return required.all { term -> tags.any(term::matches) } &&
            (alternatives.isEmpty() || alternatives.any { term -> tags.any(term::matches) }) &&
            excluded.none { term -> tags.any(term::matches) }
    }

    companion object {
        private val aliases = mapOf(
            "a" to "artist", "c" to "character", "char" to "character", "cos" to "cosplayer",
            "f" to "female", "g" to "group", "circle" to "group", "l" to "language", "lang" to "language",
            "loc" to "location", "m" to "male", "x" to "mixed", "o" to "other", "p" to "parody", "series" to "parody", "r" to "reclass",
        )
        private val namespaces = aliases.values.toSet()

        fun parse(query: String): TagQuery {
            require(query.none { it in ";()*%" }) { "不支持分号、括号或通配符；用空格表示与，~ 表示或，- 表示排除。" }
            val tokens = mutableListOf<String>()
            val token = StringBuilder()
            var quoted = false
            query.trim().forEach { char ->
                when {
                    char == '"' -> quoted = !quoted
                    char.isWhitespace() && !quoted -> if (token.isNotEmpty()) {
                        tokens.add(token.toString())
                        token.clear()
                    }
                    else -> token.append(char)
                }
            }
            require(!quoted) { "引号未闭合。" }
            if (token.isNotEmpty()) tokens.add(token.toString())
            return TagQuery(
                tokens.map { raw ->
                    val mode = raw.first().takeIf { it == '~' || it == '-' } ?: '+'
                    val body = if (mode == '+') raw else raw.drop(1)
                    val prefix = body.substringBefore(':', "").lowercase()
                    val namespace = if (prefix.isEmpty() || prefix == "tag") null else aliases[prefix] ?: prefix
                    require(namespace == null || namespace in namespaces) { "不支持的本地标签类别：$prefix" }
                    val name = body.substringAfter(':').removeSuffix("$").replace('_', ' ')
                    require(name.isNotBlank() && !name.startsWith('~') && !name.startsWith('-')) { "标签条件不完整。" }
                    Term(namespace, name, body.endsWith('$'), mode)
                },
            )
        }
    }
}
