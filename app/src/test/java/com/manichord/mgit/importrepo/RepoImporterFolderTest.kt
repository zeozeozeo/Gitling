package com.manichord.mgit.importrepo

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** The filesystem variant of [RepoImporter.checkFolder], used by the open-in-place route. */
class RepoImporterFolderTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun assertProblem(expected: RepoImporter.Problem, dir: File) {
        try {
            RepoImporter.checkFolder(dir)
            fail("Expected ${expected.name} for ${dir.absolutePath}")
        } catch (e: RepoImporter.ImportException) {
            assertEquals(expected, e.problem)
        }
    }

    @Test
    fun acceptsFolderWithGitDirectory() {
        val repo = temp.newFolder("repo")
        File(repo, ".git").mkdirs()
        RepoImporter.checkFolder(repo)
    }

    @Test
    fun rejectsFolderWithoutGit() {
        assertProblem(RepoImporter.Problem.NOT_A_REPO, temp.newFolder("plain"))
    }

    @Test
    fun rejectsPickedGitDirectory() {
        val gitDir = File(temp.newFolder("repo"), ".git")
        gitDir.mkdirs()
        File(gitDir, "HEAD").writeText("ref: refs/heads/main\n")
        File(gitDir, "objects").mkdirs()
        File(gitDir, "refs").mkdirs()
        assertProblem(RepoImporter.Problem.PICKED_GIT_DIR, gitDir)
    }

    @Test
    fun rejectsBareRepository() {
        val bare = temp.newFolder("bare.git")
        File(bare, "HEAD").writeText("ref: refs/heads/main\n")
        File(bare, "objects").mkdirs()
        File(bare, "refs").mkdirs()
        assertProblem(RepoImporter.Problem.BARE_REPO, bare)
    }

    @Test
    fun rejectsGitFileWorktree() {
        val worktree = temp.newFolder("worktree")
        File(worktree, ".git").writeText("gitdir: /elsewhere/.git/worktrees/worktree\n")
        assertProblem(RepoImporter.Problem.GIT_FILE, worktree)
    }

    @Test
    fun rejectsMissingFolder() {
        assertProblem(RepoImporter.Problem.READ_FAILED, File(temp.root, "does-not-exist"))
    }
}
