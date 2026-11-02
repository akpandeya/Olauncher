package app.olauncher

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Guards the CLAUDE.md rule that no unit test reads an API key or opens a real connection:
 * no source under `app/src` may read an environment variable or a system property, or name a
 * benchmark/API key.
 *
 * The old live `AiBenchmark`/`AiChatBenchmark` harness read the key from the shell and ran paid
 * API calls inside `testDebugUnitTest`; #713 (closing #708) deleted it. This test keeps a
 * key-reading or live-calling test from slipping back in unnoticed.
 *
 * This file is skipped by its own scan, because it must read `user.dir` to find the repo root.
 */
class NoCredentialReadsTest {

    // Built from parts so this file does not literally contain the tokens it forbids.
    private val forbiddenTokens = listOf(
        "System.get" + "env",
        "System.get" + "Property",
        "AI_BENCH" + "_KEY",
        "MELIOUS_API" + "_KEY",
    )

    @Test
    fun `no source under app reads a credential or a system property`() {
        val root = repoRoot()
        val offenders = mutableListOf<String>()

        for (sourceSet in listOf("main", "test", "androidTest")) {
            val dir = File(root, "app/src/$sourceSet")
            if (!dir.isDirectory) continue
            dir.walkTopDown()
                .filter { it.isFile && it.extension in setOf("kt", "java") }
                .filter { it.name != SELF }
                .forEach { file ->
                    val code = stripComments(file.readText())
                    for (token in forbiddenTokens) {
                        if (code.contains(token)) offenders += "${file.relativeTo(root)}: $token"
                    }
                }
        }

        assertTrue(
            "Source must not read credentials or reach the network. Found:\n" +
                offenders.joinToString("\n"),
            offenders.isEmpty(),
        )
    }

    /** Drops line and block comments so a mention of a token in a comment cannot trip the scan. */
    private fun stripComments(text: String): String =
        text.replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "")
            .lines()
            .joinToString("\n") { it.substringBefore("//") }

    /** Walks up from the test working directory to the repo root (the one with `settings.gradle`). */
    private fun repoRoot(): File {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        while (dir != null) {
            if (File(dir, "settings.gradle").isFile || File(dir, "settings.gradle.kts").isFile) return dir
            dir = dir.parentFile
        }
        error("Could not locate the repo root (settings.gradle) from ${System.getProperty("user.dir")}")
    }

    private companion object {
        const val SELF = "NoCredentialReadsTest.kt"
    }
}
