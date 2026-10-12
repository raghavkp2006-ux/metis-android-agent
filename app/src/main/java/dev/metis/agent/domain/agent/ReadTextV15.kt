package dev.metis.agent.domain.agent

/** Literal Unicode words; no synonyms, inferred identities or executable operators. */
object ReadTextV15 {
    private val words = Regex("[\\p{L}\\p{N}][\\p{L}\\p{N}\\p{M}]*")
    private val negation = Regex("\\b(?:not|never|won['’]t|wouldn['’]t|don['’]t|can['’]t)\\b", RegexOption.IGNORE_CASE)
    fun matches(content: String, subject: String, negative: Boolean = false, commitment: Boolean = false): Boolean {
        if (subject.isEmpty()) return true
        val tokens = words.findAll(subject).map { it.value.lowercase() }.toList()
        val stored = words.findAll(content).map { it.value.lowercase() }.toList()
        return tokens.isNotEmpty() && if (negative) stored.windowed(tokens.size).any { it == tokens }
            else (!commitment || !negation.containsMatchIn(content)) && tokens.all { it in stored }
    }
}
