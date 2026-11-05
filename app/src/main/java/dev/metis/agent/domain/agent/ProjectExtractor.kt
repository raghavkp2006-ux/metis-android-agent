package dev.metis.agent.domain.agent

data class ProjectExtractionResult(
    val cleanText: String,
    val projectName: String?,
)

object ProjectExtractor {
    private val PATTERNS = listOf(
        // "for/in the <name> project" e.g., "for the Apollo project", "in the Project X project"
        Regex("""\b(?:for|in)\s+the\s+(.+?)\s+project\b""", RegexOption.IGNORE_CASE),
        // "for/in the Project <Name>" e.g., "in the Project X", "for the Project Alpha"
        Regex("""\b(?:for|in)\s+the\s+(Project\s+[A-Za-z0-9_-]+)\b""", RegexOption.IGNORE_CASE),
        // "for/in Project <Name>" e.g., "for Project Beta"
        Regex("""\b(?:for|in)\s+(Project\s+[A-Za-z0-9_-]+)\b""", RegexOption.IGNORE_CASE),
        // "for/in project <Name>" e.g., "for project Apollo"
        Regex("""\b(?:for|in)\s+project\s+([A-Za-z0-9_-]+)\b""", RegexOption.IGNORE_CASE),
    )

    fun extract(text: String): ProjectExtractionResult {
        var clean = text.trim()
        var extractedProject: String? = null
        for (pattern in PATTERNS) {
            val match = pattern.find(clean)
            if (match != null) {
                extractedProject = match.groupValues[1].trim()
                clean = (clean.substring(0, match.range.first) + " " + clean.substring(match.range.last + 1)).trim()
                clean = clean.replace(Regex("""\s+"""), " ")
                break
            }
        }
        return ProjectExtractionResult(clean, extractedProject?.takeIf { it.isNotBlank() })
    }
}
