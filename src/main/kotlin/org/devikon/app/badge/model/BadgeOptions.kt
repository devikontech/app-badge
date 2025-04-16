package org.devikon.app.badge.model

import java.awt.Color
import java.awt.Font
import java.io.File
import java.io.Serializable

/**
 * Enhanced configuration options for badge generation with templates and custom shapes.
 */
data class BadgeOptions(
    val text: String,
    val backgroundColor: Color = Color.WHITE,
    val textColor: Color = Color(102, 102, 102), // #666666
    val shadowColor: Color = Color(0, 0, 0, 153), // rgba(0,0,0,0.6)
    val fontSize: Int = 28,
    val gravity: BadgeGravity = BadgeGravity.SOUTHEAST,
    val font: Font? = null,
    val fontFile: File? = null,
    val position: Position? = null,
    val paddingX: Int = 6,
    val paddingY: Int = 6,
    val shadowSize: Int = 4,
    val shape: BadgeShape = BadgeShape.RECTANGLE,
    val borderRadius: Int = 4,
    val borderColor: Color? = null,
    val borderWidth: Int = 0,
    val useGradient: Boolean = false,
    val gradientEndColor: Color? = null,
    val opacity: Float = 1.0f
) : Serializable {

    /**
     * Position can be specified as a percentage along an axis or as explicit x,y coordinates
     */
    data class Position(
        val x: Int,
        val y: Int? = null
    ) : Serializable

    /**
     * Scale the badge options based on the target image size
     * (since defaults are based on 192px images)
     */
    fun scaled(scale: Double): BadgeOptions {
        return copy(
            fontSize = (fontSize * scale).toInt(),
            paddingX = (paddingX * scale).toInt().evenRounded(),
            paddingY = (paddingY * scale).toInt().evenRounded(),
            shadowSize = (shadowSize * scale).toInt().coerceAtLeast(1),
            borderRadius = (borderRadius * scale).toInt().coerceAtLeast(1),
            borderWidth = (borderWidth * scale).toInt().coerceAtLeast(if (borderWidth > 0) 1 else 0)
        )
    }

    /**
     * Create a copy with position coordinates in standard format.
     */
    fun withStandardizedPosition(): BadgeOptions {
        val pos = position ?: return this
        return if (pos.y != null) {
            this // Already has both x,y
        } else {
            // Convert single coordinate to x,y based on gravity
            val y = when (gravity) {
                BadgeGravity.NORTH, BadgeGravity.NORTHEAST, BadgeGravity.NORTHWEST -> pos.x
                BadgeGravity.SOUTH, BadgeGravity.SOUTHEAST, BadgeGravity.SOUTHWEST -> 100 - pos.x
            }
            copy(position = Position(pos.x, y))
        }
    }

    /**
     * Available badge shapes
     */
    enum class BadgeShape {
        RECTANGLE,
        ROUNDED_RECTANGLE,
        PILL,
        CIRCLE,
        TRIANGLE
    }

    companion object {
        /**
         * Round a number to the nearest even integer
         */
        private fun Int.evenRounded(): Int = (this / 2) * 2

        /**
         * Predefined badge templates
         */
        val TEMPLATES = mapOf(
            "Default" to BadgeOptions("SAMPLE"),

            "Development" to BadgeOptions(
                text = "DEV",
                backgroundColor = Color(41, 128, 185),
                textColor = Color.WHITE,
                shape = BadgeShape.ROUNDED_RECTANGLE
            ),

            "Testing" to BadgeOptions(
                text = "TEST",
                backgroundColor = Color(243, 156, 18),
                textColor = Color.WHITE,
                shape = BadgeShape.ROUNDED_RECTANGLE
            ),

            "Staging" to BadgeOptions(
                text = "STAGING",
                backgroundColor = Color(142, 68, 173),
                textColor = Color.WHITE,
                shape = BadgeShape.ROUNDED_RECTANGLE
            ),

            "Alpha" to BadgeOptions(
                text = "ALPHA",
                backgroundColor = Color(231, 76, 60),
                textColor = Color.WHITE,
                shape = BadgeShape.PILL
            ),

            "Beta" to BadgeOptions(
                text = "BETA",
                backgroundColor = Color(46, 204, 113),
                textColor = Color.WHITE,
                shape = BadgeShape.PILL
            ),

            "Minimal" to BadgeOptions(
                text = "v1.0",
                backgroundColor = Color(0, 0, 0, 80),
                textColor = Color.WHITE,
                shadowSize = 0,
                borderRadius = 2,
                fontSize = 20
            ),

            "Gradient" to BadgeOptions(
                text = "PREVIEW",
                backgroundColor = Color(41, 128, 185),
                textColor = Color.WHITE,
                useGradient = true,
                gradientEndColor = Color(52, 152, 219),
                shape = BadgeShape.ROUNDED_RECTANGLE
            ),

            "Outlined" to BadgeOptions(
                text = "RC",
                backgroundColor = Color(255, 255, 255, 220),
                textColor = Color(44, 62, 80),
                borderColor = Color(44, 62, 80),
                borderWidth = 2,
                shape = BadgeShape.ROUNDED_RECTANGLE
            ),

            "Version Tag" to BadgeOptions(
                text = "v2.3.0",
                backgroundColor = Color(0, 0, 0, 220),
                textColor = Color.WHITE,
                fontSize = 20,
                shape = BadgeShape.PILL,
                shadowSize = 2
            )
        )

        /**
         * Get a template by name
         */
        fun fromTemplate(name: String, customText: String? = null): BadgeOptions {
            val template = TEMPLATES[name] ?: TEMPLATES["Default"]!!
            return if (customText != null) {
                template.copy(text = customText)
            } else {
                template
            }
        }

        /**
         * Detect environment from project structure and suggest appropriate badge text
         */
        fun detectEnvironment(projectPath: String): String {
            val lowerPath = projectPath.lowercase()
            return when {
                lowerPath.contains("debug") -> "DEBUG"
                lowerPath.contains("dev") -> "DEV"
                lowerPath.contains("test") -> "TEST"
                lowerPath.contains("staging") -> "STAGING"
                lowerPath.contains("alpha") -> "ALPHA"
                lowerPath.contains("beta") -> "BETA"
                lowerPath.contains("rc") -> "RC"
                else -> "DEV"
            }
        }
    }
}