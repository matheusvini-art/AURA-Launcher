package com.auralauncher.backup

import java.io.StringReader
import java.io.StringWriter
import java.util.Properties

/**
 * Versioned, text-based snapshot used by the local export/import flow.
 * Keep this format independent from Android so it can be validated by JVM tests.
 */
data class LauncherBackup(
    val favorites: Set<String> = emptySet(),
    val hidden: Set<String> = emptySet(),
    val recentPackages: List<String> = emptyList(),
    val foldersJson: String = "[]",
    val iconSizeDp: Float = 54f,
    val columns: Int = 4,
    val labelsEnabled: Boolean = true,
    val accentIndex: Int = 0,
    val themeMode: Int = 0,
    val sortMode: Int = 0,
    val animationsEnabled: Boolean = true,
    val iconPackPackage: String? = null,
    val swipeLeftGestureEnabled: Boolean = true,
    val doubleTapSettingsEnabled: Boolean = true
)

object LauncherBackupCodec {
    private const val SCHEMA_VERSION = 2
    private const val MAX_TEXT_LENGTH = 512_000

    fun encode(backup: LauncherBackup): String {
        validate(backup)
        val properties = Properties().apply {
            setProperty("schema", SCHEMA_VERSION.toString())
            setProperty("favorites", backup.favorites.sorted().joinToString("\n"))
            setProperty("hidden", backup.hidden.sorted().joinToString("\n"))
            setProperty("recents", backup.recentPackages.joinToString("\n"))
            setProperty("folders", backup.foldersJson)
            setProperty("iconSizeDp", backup.iconSizeDp.toString())
            setProperty("columns", backup.columns.toString())
            setProperty("labelsEnabled", backup.labelsEnabled.toString())
            setProperty("accentIndex", backup.accentIndex.toString())
            setProperty("themeMode", backup.themeMode.toString())
            setProperty("sortMode", backup.sortMode.toString())
            setProperty("animationsEnabled", backup.animationsEnabled.toString())
            setProperty("iconPackPackage", backup.iconPackPackage.orEmpty())
            setProperty("swipeLeftGestureEnabled", backup.swipeLeftGestureEnabled.toString())
            setProperty("doubleTapSettingsEnabled", backup.doubleTapSettingsEnabled.toString())
        }
        return StringWriter().also { properties.store(it, "AURA Launcher backup") }.toString()
    }

    fun decode(raw: String): LauncherBackup {
        require(raw.length <= MAX_TEXT_LENGTH) { "Backup file is too large." }
        val properties = Properties().apply { load(StringReader(raw)) }
        val schema = properties.getProperty("schema")?.toIntOrNull()
        require(schema in 1..SCHEMA_VERSION) {
            "Unsupported or missing AURA backup version."
        }
        fun list(key: String) = properties.getProperty(key, "")
            .split('\n').map(String::trim).filter(String::isNotEmpty).toSet()

        val backup = LauncherBackup(
            favorites = list("favorites"),
            hidden = list("hidden"),
            recentPackages = properties.getProperty("recents", "").split('\n')
                .map(String::trim).filter(String::isNotEmpty),
            foldersJson = properties.getProperty("folders", "[]"),
            iconSizeDp = properties.getProperty("iconSizeDp", "54").toFloatOrNull()
                ?: error("Invalid icon size."),
            columns = properties.getProperty("columns", "4").toIntOrNull()
                ?: error("Invalid column count."),
            labelsEnabled = properties.getProperty("labelsEnabled", "true").toBooleanStrict(),
            accentIndex = properties.getProperty("accentIndex", "0").toIntOrNull()
                ?: error("Invalid accent index."),
            themeMode = properties.getProperty("themeMode", "0").toIntOrNull()
                ?: error("Invalid theme mode."),
            sortMode = properties.getProperty("sortMode", "0").toIntOrNull()
                ?: error("Invalid sort mode."),
            animationsEnabled = properties.getProperty("animationsEnabled", "true").toBooleanStrict(),
            iconPackPackage = properties.getProperty("iconPackPackage", "").ifBlank { null },
            swipeLeftGestureEnabled = properties.getProperty("swipeLeftGestureEnabled", "true").toBooleanStrict(),
            doubleTapSettingsEnabled = properties.getProperty("doubleTapSettingsEnabled", "true").toBooleanStrict()
        )
        validate(backup)
        return backup
    }

    private fun validate(backup: LauncherBackup) {
        require(backup.iconSizeDp in 32f..96f) { "Icon size is outside the supported range." }
        require(backup.columns in 2..8) { "Column count is outside the supported range." }
        require(backup.accentIndex in 0..31) { "Accent index is invalid." }
        require(backup.themeMode in 0..4) { "Theme mode is invalid." }
        require(backup.sortMode in 0..4) { "Sort mode is invalid." }
        require(backup.foldersJson.length <= 128_000) { "Folder data is too large." }
        require((backup.iconPackPackage?.length ?: 0) <= 255 && backup.iconPackPackage?.contains('\n') != true) {
            "Icon pack package name is invalid."
        }
        require((backup.favorites + backup.hidden + backup.recentPackages).all {
            it.length <= 255 && !it.contains('\n') && !it.contains('\r')
        }) { "Package name is invalid." }
    }
}
