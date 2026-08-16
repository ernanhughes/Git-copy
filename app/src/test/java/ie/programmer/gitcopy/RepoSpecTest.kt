package ie.programmer.gitcopy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RepoSpecTest {
    @Test
    fun parsesNormalGithubUrl() {
        assertEquals(
            RepoSpec("ernanhughes", "Git-copy"),
            RepoSpec.parse("https://github.com/ernanhughes/Git-copy"),
        )
    }

    @Test
    fun parsesDotGitUrl() {
        assertEquals(
            RepoSpec("owner", "repo"),
            RepoSpec.parse("https://github.com/owner/repo.git"),
        )
    }

    @Test
    fun rejectsNonGithubUrl() {
        assertNull(RepoSpec.parse("https://example.com/owner/repo"))
    }
}
