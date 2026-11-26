package com.auralauncher.icons

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.content.res.XmlResourceParser
import org.xmlpull.v1.XmlPullParser

data class InstalledIconPack(
    val packageName: String,
    val label: String
)

/**
 * Reads the common ADW/Nova-style appfilter.xml mapping used by many icon packs.
 * Unsupported packs safely fall back to the app's original icon.
 */
object IconPackManager {
    private val themeActions = listOf(
        "org.adw.launcher.THEMES",
        "com.novalauncher.THEME",
        "com.anddoes.launcher.THEME",
        "com.teslacoilsw.launcher.THEME"
    )

    @Suppress("DEPRECATION")
    fun installedPacks(context: Context): List<InstalledIconPack> {
        val pm = context.packageManager
        return themeActions.flatMap { action ->
            pm.queryIntentActivities(Intent(action), PackageManager.MATCH_DEFAULT_ONLY)
                .mapNotNull { resolveInfo ->
                    val packageName = resolveInfo.activityInfo?.packageName ?: return@mapNotNull null
                    InstalledIconPack(
                        packageName = packageName,
                        label = resolveInfo.loadLabel(pm).toString()
                    )
                }
        }.distinctBy { it.packageName }.sortedBy { it.label.lowercase() }
    }

    fun loadDrawable(
        context: Context,
        targetPackage: String,
        targetClass: String,
        iconPackPackage: String?
    ): Drawable? {
        if (iconPackPackage.isNullOrBlank()) return null
        return runCatching {
            val packContext = context.createPackageContext(iconPackPackage, Context.CONTEXT_RESTRICTED)
            val mappings = readMappings(packContext)
            val drawableName = mappings["$targetPackage/$targetClass"]
                ?: mappings["$targetPackage/*"]
                ?: mappings[targetPackage]
                ?: return null
            val id = packContext.resources.getIdentifier(drawableName, "drawable", iconPackPackage)
            if (id == 0) return null
            packContext.getDrawable(id)
        }.getOrNull()
    }

    private fun readMappings(packContext: Context): Map<String, String> {
        val resources = packContext.resources
        val xmlId = resources.getIdentifier("appfilter", "xml", packContext.packageName)
        if (xmlId == 0) return emptyMap()
        val result = linkedMapOf<String, String>()
        val parser: XmlResourceParser = resources.getXml(xmlId)
        parser.use {
            var event = it.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG && it.name == "item") {
                    val component = it.getAttributeValue(null, "component").orEmpty()
                    val drawable = it.getAttributeValue(null, "drawable").orEmpty()
                    if (component.startsWith("ComponentInfo{") && drawable.isNotBlank()) {
                        val value = component.removePrefix("ComponentInfo{").removeSuffix("}")
                        val parts = value.split("/", limit = 2)
                        if (parts.size == 2) {
                            val className = if (parts[1].startsWith(".")) parts[0] + parts[1] else parts[1]
                            result["${parts[0]}/$className"] = drawable
                            result["${parts[0]}/*"] = drawable
                            result.putIfAbsent(parts[0], drawable)
                        }
                    }
                }
                event = it.next()
            }
        }
        return result
    }
}
