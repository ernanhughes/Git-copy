package ie.programmer.gitcopy

data class RepoSpec(
    val owner: String,
    val name: String,
) {
    val archiveUrl: String
        get() = "https://api.github.com/repos/$owner/$name/zipball"

    companion object {
        private val githubUrl = Regex(
            pattern = "^https?://(?:www\\.)?github\\.com/([A-Za-z0-9_.-]+)/([A-Za-z0-9_.-]+?)(?:\\.git)?/?$",
            option = RegexOption.IGNORE_CASE,
        )

        fun parse(raw: String): RepoSpec? {
            val value = raw.trim()
            val match = githubUrl.matchEntire(value) ?: return null
            val owner = match.groupValues[1]
            val repo = match.groupValues[2]
            if (owner.isBlank() || repo.isBlank()) return null
            return RepoSpec(owner = owner, name = repo)
        }
    }
}
