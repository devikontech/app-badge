package org.devikon.app.badge.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil
import org.devikon.app.badge.model.BadgeGravity
import org.devikon.app.badge.model.BadgeOptions
import java.io.File

/**
 * Persistent settings for the Badge Generator plugin.
 */
@Service
@State(
    name = "BadgeGeneratorSettings",
    storages = [Storage("badgeGeneratorSettings.xml")]
)
class BadgeSettings : PersistentStateComponent<BadgeSettings> {

    // Global default settings
    var defaultTemplate: String = "Default"
    var defaultGravity: BadgeGravity = BadgeGravity.SOUTHEAST
    var defaultFontSize: Int = 28
    var defaultTextColor: String = "#666666"
    var defaultBackgroundColor: String = "#FFFFFF"
    var defaultShadowColor: String = "rgba(0,0,0,0.6)"
    var defaultBorderRadius: Int = 4
    var defaultShape: BadgeOptions.BadgeShape = BadgeOptions.BadgeShape.RECTANGLE

    // Performance settings
    var enableCaching: Boolean = true
    var maxCacheSize: Int = 50

    // Customization
    var customTemplates: MutableList<SerializableTemplate> = mutableListOf()
    var recentlyUsedOptions: MutableList<SerializableOptions> = mutableListOf()
    var recentFontPaths: MutableList<String> = mutableListOf()

    // Integration settings
    var integrateWithAndroidBuildVariants: Boolean = true
    var integrateWithGitBranches: Boolean = true

    override fun getState(): BadgeSettings = this

    override fun loadState(state: BadgeSettings) {
        XmlSerializerUtil.copyBean(state, this)
    }

    /**
     * Get default badge options based on settings.
     */
    fun getDefaultBadgeOptions(text: String = "DEV"): BadgeOptions {
        return BadgeOptions(
            text = text,
            backgroundColor = parseColor(defaultBackgroundColor),
            textColor = parseColor(defaultTextColor),
            shadowColor = parseColor(defaultShadowColor),
            fontSize = defaultFontSize,
            gravity = defaultGravity,
            borderRadius = defaultBorderRadius,
            shape = defaultShape
        )
    }

    /**
     * Save custom template.
     */
    fun saveCustomTemplate(name: String, options: BadgeOptions) {
        val existingIndex = customTemplates.indexOfFirst { it.name == name }
        val serialized = SerializableTemplate.fromBadgeOptions(name, options)

        if (existingIndex >= 0) {
            customTemplates[existingIndex] = serialized
        } else {
            customTemplates.add(serialized)
        }
    }

    /**
     * Get all available templates (built-in + custom).
     */
    fun getAllTemplates(): Map<String, BadgeOptions> {
        val templates = BadgeOptions.TEMPLATES.toMutableMap()

        // Add custom templates
        customTemplates.forEach { template ->
            templates[template.name] = template.toBadgeOptions()
        }

        return templates
    }

    /**
     * Add font path to recent list.
     */
    fun addRecentFontPath(path: String) {
        if (path.isBlank()) return

        // Remove existing and add to front (most recent)
        recentFontPaths.remove(path)
        recentFontPaths.add(0, path)

        // Keep list at reasonable size
        if (recentFontPaths.size > 10) {
            recentFontPaths = recentFontPaths.take(10).toMutableList()
        }
    }

    /**
     * Add to recently used options.
     */
    fun addRecentOptions(options: BadgeOptions) {
        // Create serializable version
        val serialized = SerializableOptions.fromBadgeOptions(options)

        // Add to front (most recent)
        recentlyUsedOptions.add(0, serialized)

        // Keep list at reasonable size
        if (recentlyUsedOptions.size > 10) {
            recentlyUsedOptions = recentlyUsedOptions.take(10).toMutableList()
        }
    }

    /**
     * Get recently used options.
     */
    fun getRecentOptions(): List<BadgeOptions> {
        return recentlyUsedOptions.map { it.toBadgeOptions() }
    }

    /**
     * Parse color string to Color object.
     */
    fun parseColor(colorStr: String): java.awt.Color {
        return try {
            when {
                colorStr.startsWith("#") -> {
                    val hex = colorStr.substring(1)
                    when (hex.length) {
                        3 -> {
                            val r = hex.substring(0, 1).repeat(2).toInt(16)
                            val g = hex.substring(1, 2).repeat(2).toInt(16)
                            val b = hex.substring(2, 3).repeat(2).toInt(16)
                            java.awt.Color(r, g, b)
                        }
                        6 -> {
                            val r = hex.substring(0, 2).toInt(16)
                            val g = hex.substring(2, 4).toInt(16)
                            val b = hex.substring(4, 6).toInt(16)
                            java.awt.Color(r, g, b)
                        }
                        8 -> {
                            val r = hex.substring(0, 2).toInt(16)
                            val g = hex.substring(2, 4).toInt(16)
                            val b = hex.substring(4, 6).toInt(16)
                            val a = hex.substring(6, 8).toInt(16)
                            java.awt.Color(r, g, b, a)
                        }
                        else -> java.awt.Color.BLACK
                    }
                }
                colorStr.startsWith("rgba") -> {
                    val parts = colorStr
                        .removePrefix("rgba(")
                        .removeSuffix(")")
                        .split(",")
                        .map { it.trim() }

                    if (parts.size == 4) {
                        val r = parts[0].toInt()
                        val g = parts[1].toInt()
                        val b = parts[2].toInt()
                        val a = (parts[3].toFloat() * 255).toInt()
                        java.awt.Color(r, g, b, a)
                    } else {
                        java.awt.Color.BLACK
                    }
                }
                else -> java.awt.Color.BLACK
            }
        } catch (e: Exception) {
            java.awt.Color.BLACK
        }
    }

    /**
     * Serializable version of a badge template.
     */
    data class SerializableTemplate(
        var name: String = "",
        var text: String = "SAMPLE",
        var backgroundColor: String = "#FFFFFF",
        var textColor: String = "#666666",
        var shadowColor: String = "rgba(0,0,0,0.6)",
        var fontSize: Int = 28,
        var gravity: String = BadgeGravity.SOUTHEAST.name,
        var shape: String = BadgeOptions.BadgeShape.RECTANGLE.name,
        var borderRadius: Int = 4,
        var borderWidth: Int = 0,
        var borderColor: String? = null,
        var useGradient: Boolean = false,
        var gradientEndColor: String? = null,
        var opacity: Float = 1.0f,
        var shadowSize: Int = 4
    ) {
        fun toBadgeOptions(): BadgeOptions {
            return BadgeOptions(
                text = text,
                backgroundColor = parseColor(backgroundColor),
                textColor = parseColor(textColor),
                shadowColor = parseColor(shadowColor),
                fontSize = fontSize,
                gravity = BadgeGravity.valueOf(gravity),
                borderRadius = borderRadius,
                shape = BadgeOptions.BadgeShape.valueOf(shape),
                borderWidth = borderWidth,
                borderColor = borderColor?.let { parseColor(it) },
                useGradient = useGradient,
                gradientEndColor = gradientEndColor?.let { parseColor(it) },
                opacity = opacity,
                shadowSize = shadowSize
            )
        }

        companion object {
            fun fromBadgeOptions(name: String, options: BadgeOptions): SerializableTemplate {
                return SerializableTemplate(
                    name = name,
                    text = options.text,
                    backgroundColor = colorToString(options.backgroundColor),
                    textColor = colorToString(options.textColor),
                    shadowColor = colorToString(options.shadowColor),
                    fontSize = options.fontSize,
                    gravity = options.gravity.name,
                    shape = options.shape.name,
                    borderRadius = options.borderRadius,
                    borderWidth = options.borderWidth,
                    borderColor = options.borderColor?.let { colorToString(it) },
                    useGradient = options.useGradient,
                    gradientEndColor = options.gradientEndColor?.let { colorToString(it) },
                    opacity = options.opacity,
                    shadowSize = options.shadowSize
                )
            }

            fun colorToString(color: java.awt.Color): String {
                return if (color.alpha == 255) {
                    String.format("#%02X%02X%02X", color.red, color.green, color.blue)
                } else {
                    String.format("rgba(%d,%d,%d,%.2f)",
                        color.red, color.green, color.blue, color.alpha / 255.0f)
                }
            }

            fun parseColor(colorStr: String): java.awt.Color {
                return try {
                    when {
                        colorStr.startsWith("#") -> {
                            val hex = colorStr.substring(1)
                            when (hex.length) {
                                3 -> {
                                    val r = hex.substring(0, 1).repeat(2).toInt(16)
                                    val g = hex.substring(1, 2).repeat(2).toInt(16)
                                    val b = hex.substring(2, 3).repeat(2).toInt(16)
                                    java.awt.Color(r, g, b)
                                }
                                6 -> {
                                    val r = hex.substring(0, 2).toInt(16)
                                    val g = hex.substring(2, 4).toInt(16)
                                    val b = hex.substring(4, 6).toInt(16)
                                    java.awt.Color(r, g, b)
                                }
                                8 -> {
                                    val r = hex.substring(0, 2).toInt(16)
                                    val g = hex.substring(2, 4).toInt(16)
                                    val b = hex.substring(4, 6).toInt(16)
                                    val a = hex.substring(6, 8).toInt(16)
                                    java.awt.Color(r, g, b, a)
                                }
                                else -> java.awt.Color.BLACK
                            }
                        }
                        colorStr.startsWith("rgba") -> {
                            val parts = colorStr
                                .removePrefix("rgba(")
                                .removeSuffix(")")
                                .split(",")
                                .map { it.trim() }

                            if (parts.size == 4) {
                                val r = parts[0].toInt()
                                val g = parts[1].toInt()
                                val b = parts[2].toInt()
                                val a = (parts[3].toFloat() * 255).toInt()
                                java.awt.Color(r, g, b, a)
                            } else {
                                java.awt.Color.BLACK
                            }
                        }
                        else -> java.awt.Color.BLACK
                    }
                } catch (e: Exception) {
                    java.awt.Color.BLACK
                }
            }
        }
    }

    /**
     * Serializable version of badge options.
     */
    data class SerializableOptions(
        var text: String = "SAMPLE",
        var backgroundColor: String = "#FFFFFF",
        var textColor: String = "#666666",
        var shadowColor: String = "rgba(0,0,0,0.6)",
        var fontSize: Int = 28,
        var gravity: String = BadgeGravity.SOUTHEAST.name,
        var fontFile: String? = null,
        var shape: String = BadgeOptions.BadgeShape.RECTANGLE.name,
        var borderRadius: Int = 4,
        var borderWidth: Int = 0,
        var borderColor: String? = null,
        var useGradient: Boolean = false,
        var gradientEndColor: String? = null,
        var opacity: Float = 1.0f,
        var shadowSize: Int = 4
    ) {
        fun toBadgeOptions(): BadgeOptions {
            return BadgeOptions(
                text = text,
                backgroundColor = SerializableTemplate.parseColor(backgroundColor),
                textColor = SerializableTemplate.parseColor(textColor),
                shadowColor = SerializableTemplate.parseColor(shadowColor),
                fontSize = fontSize,
                gravity = BadgeGravity.valueOf(gravity),
                fontFile = fontFile?.let { File(it) },
                borderRadius = borderRadius,
                shape = BadgeOptions.BadgeShape.valueOf(shape),
                borderWidth = borderWidth,
                borderColor = borderColor?.let { SerializableTemplate.parseColor(it) },
                useGradient = useGradient,
                gradientEndColor = gradientEndColor?.let { SerializableTemplate.parseColor(it) },
                opacity = opacity,
                shadowSize = shadowSize
            )
        }

        companion object {
            fun fromBadgeOptions(options: BadgeOptions): SerializableOptions {
                return SerializableOptions(
                    text = options.text,
                    backgroundColor = SerializableTemplate.colorToString(options.backgroundColor),
                    textColor = SerializableTemplate.colorToString(options.textColor),
                    shadowColor = SerializableTemplate.colorToString(options.shadowColor),
                    fontSize = options.fontSize,
                    gravity = options.gravity.name,
                    fontFile = options.fontFile?.absolutePath,
                    shape = options.shape.name,
                    borderRadius = options.borderRadius,
                    borderWidth = options.borderWidth,
                    borderColor = options.borderColor?.let { SerializableTemplate.colorToString(it) },
                    useGradient = options.useGradient,
                    gradientEndColor = options.gradientEndColor?.let { SerializableTemplate.colorToString(it) },
                    opacity = options.opacity,
                    shadowSize = options.shadowSize
                )
            }
        }
    }
}