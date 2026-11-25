package com.auralauncher.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class LauncherBackupCodecTest {
    @Test
    fun roundTripPreservesLauncherPreferences() {
        val original = LauncherBackup(
            favorites = setOf("com.example.mail", "com.example.music"),
            hidden = setOf("com.example.private"),
            recentPackages = listOf("com.example.mail", "com.example.camera"),
            foldersJson = """[{"name":"Trabalho","packages":["com.example.mail"]}]""",
            iconSizeDp = 61f,
            columns = 5,
            labelsEnabled = false,
            accentIndex = 3,
            themeMode = 1,
            sortMode = 1,
            animationsEnabled = false
        )

        assertEquals(original, LauncherBackupCodec.decode(LauncherBackupCodec.encode(original)))
    }

    @Test
    fun rejectsUnknownSchema() {
        assertThrows(IllegalArgumentException::class.java) {
            LauncherBackupCodec.decode("schema=99\nfavorites=\n")
        }
    }

    @Test
    fun rejectsOutOfRangeColumns() {
        assertThrows(IllegalArgumentException::class.java) {
            LauncherBackupCodec.encode(LauncherBackup(columns = 99))
        }
    }

    @Test
    fun rejectsOversizedInput() {
        assertThrows(IllegalArgumentException::class.java) {
            LauncherBackupCodec.decode("x".repeat(512_001))
        }
    }
}
