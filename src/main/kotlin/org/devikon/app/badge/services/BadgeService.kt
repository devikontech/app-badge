package org.devikon.app.badge.services
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.AppExecutorUtil
import org.devikon.app.badge.model.BadgeOptions
import org.devikon.app.badge.model.BadgePositionCalculator
import java.awt.AlphaComposite
import java.awt.BasicStroke
import java.awt.Font
import java.awt.FontFormatException
import java.awt.GradientPaint
import java.awt.RenderingHints
import java.awt.Shape
import java.awt.font.FontRenderContext
import java.awt.font.TextLayout
import java.awt.geom.Ellipse2D
import java.awt.geom.Path2D
import java.awt.geom.Rectangle2D
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future

/**
 * Enhanced service for generating and applying badges to images with advanced features.
 */
@Service
class BadgeService {
    private val logger = Logger.getInstance(BadgeService::class.java)
    private val imageService = service<ImageService>()

    // Font cache to avoid reloading the same font
    private val fontCache = ConcurrentHashMap<String, Font>()

    // Badge cache for frequently used badges
    private val badgeCache = ConcurrentHashMap<String, BufferedImage>()

    // Thread pool for parallel processing
    private val executorService: ExecutorService = AppExecutorUtil.createBoundedApplicationPoolExecutor(
        "Badge Service",
        Runtime.getRuntime().availableProcessors()
    )

    /**
     * Add a badge to an image file.
     */
    fun addBadgeToImage(imageFile: File, options: BadgeOptions): Boolean {
        val image = imageService.loadImage(imageFile) ?: return false

        val badgedImage = addBadgeToImage(image, options)
        return imageService.saveImage(badgedImage, imageFile)
    }

    /**
     * Add a badge to a BufferedImage with advanced features.
     */
    fun addBadgeToImage(image: BufferedImage, options: BadgeOptions): BufferedImage {
        // Calculate the inset width (find the non-transparent area)
        val eastInset = imageService.findInset(image, ImageService.Direction.EAST)
        val westInset = imageService.findInset(image, ImageService.Direction.WEST)
        val insetWidth = image.width - eastInset - westInset

        // Scale options based on image size (assumes default values are for 192px icons)
        val scale = insetWidth / 192.0
        val scaledOptions = options.scaled(scale)

        // Create the badge
        val badge = createBadgeImage(scaledOptions)

        // Add shadow to the badge if shadow size > 0
        val badgeWithShadow = if (scaledOptions.shadowSize > 0) {
            imageService.addShadow(
                badge,
                scaledOptions.shadowColor,
                scaledOptions.shadowSize
            )
        } else {
            badge
        }

        // Calculate the badge position
        val position = if (options.position != null) {
            BadgePositionCalculator.calculateManualBadgePosition(
                image.width,
                image.height,
                badge.width,
                badge.height,
                options.position,
                options.gravity
            )
        } else {
            // Default to circular positioning
            val radius = insetWidth / 2
            BadgePositionCalculator.calculateCircularBadgePosition(
                image.width,
                image.height,
                badge.width,
                badge.height,
                radius,
                options.gravity
            )
        }

        // Rotate the badge if needed
        val rotatedBadge = if (position.rotation != 0) {
            imageService.rotateImage(badgeWithShadow, position.rotation)
        } else {
            badgeWithShadow
        }

        // Composite the badge onto the image with proper alpha
        val result = imageService.createTransparentImage(image.width, image.height)
        val g = result.createGraphics()

        // Enable high quality rendering
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)

        // Draw the original image
        g.drawImage(image, 0, 0, null)

        // Apply global opacity if needed
        if (options.opacity < 1.0f) {
            g.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, options.opacity)
        }

        // Draw the badge at the calculated position
        g.drawImage(rotatedBadge, position.point.x, position.point.y, null)

        g.dispose()
        return result
    }

    /**
     * Process multiple image files with the same badge options asynchronously.
     */
    fun processBatchImagesAsync(imageFiles: List<File>, options: BadgeOptions): Future<Int> {
        return executorService.submit<Int> {
            var successCount = 0

            for (file in imageFiles) {
                if (addBadgeToImage(file, options)) {
                    successCount++
                }
            }

            successCount
        }
    }

    /**
     * Create a badge image with the specified text and style options.
     * Uses caching for frequently used badges.
     */
    private fun createBadgeImage(options: BadgeOptions): BufferedImage {
        // Check cache for identical badges
        val cacheKey = generateBadgeCacheKey(options)
        val cachedBadge = badgeCache[cacheKey]
        if (cachedBadge != null) {
            return copyImage(cachedBadge)
        }

        // Get font to use
        val font = when {
            options.font != null -> options.font
            options.fontFile != null -> loadFont(options.fontFile)
            else -> Font("Dialog", Font.BOLD, options.fontSize)
        }

        // Measure text dimensions with precise font metrics
        val frc = FontRenderContext(null, true, true)
        val textLayout = TextLayout(options.text, font, frc)
        val bounds = textLayout.bounds

        // Calculate badge size based on shape
        val (badgeWidth, badgeHeight) = when (options.shape) {
            BadgeOptions.BadgeShape.CIRCLE -> {
                val diameter = Math.max(bounds.width.toInt(), bounds.height.toInt()) +
                        Math.max(options.paddingX, options.paddingY) * 2
                Pair(diameter, diameter)
            }
            BadgeOptions.BadgeShape.PILL -> {
                val height = bounds.height.toInt() + options.paddingY * 2
                val width = bounds.width.toInt() + options.paddingX * 2 + height/2 // Extra padding for rounded ends
                Pair(width, height)
            }
            else -> {
                val width = bounds.width.toInt() + options.paddingX * 2
                val height = bounds.height.toInt() + options.paddingY * 2
                Pair(width, height)
            }
        }

        // Create the badge image with transparency
        val badge = BufferedImage(badgeWidth, badgeHeight, BufferedImage.TYPE_INT_ARGB)
        val g = badge.createGraphics()

        // Clear the background to fully transparent
        g.composite = AlphaComposite.getInstance(AlphaComposite.CLEAR)
        g.fillRect(0, 0, badgeWidth, badgeHeight)
        g.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER)

        // Apply quality rendering hints
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON)

        // Draw shape with background
        val shape = createBadgeShape(badgeWidth, badgeHeight, options)

        if (options.useGradient && options.gradientEndColor != null) {
            // Create gradient
            val gradient = GradientPaint(
                0f, 0f, options.backgroundColor,
                badgeWidth.toFloat(), badgeHeight.toFloat(), options.gradientEndColor
            )
            g.paint = gradient
        } else {
            g.color = options.backgroundColor
        }

        g.fill(shape)

        // Draw border if specified
        if (options.borderWidth > 0 && options.borderColor != null) {
            g.color = options.borderColor
            g.stroke = BasicStroke(options.borderWidth.toFloat())
            g.draw(shape)
        }

        // Draw text with precise centering
        g.color = options.textColor
        g.font = font

        // Calculate text position (precisely centered)
        val textX = (badgeWidth - bounds.width) / 2 - bounds.x
        val textY = (badgeHeight - bounds.height) / 2 - bounds.y + textLayout.ascent

        // Draw the text at the calculated position
        textLayout.draw(g, textX.toFloat(), textY.toFloat())

        g.dispose()

        // Cache the badge if it's not too large
        if (badgeWidth * badgeHeight < MAX_CACHE_PIXELS) {
            badgeCache[cacheKey] = copyImage(badge)
        }

        return badge
    }

    /**
     * Create a shape for the badge based on the specified shape type.
     * Supports individual corner radius settings for rounded rectangle shapes.
     */
    private fun createBadgeShape(width: Int, height: Int, options: BadgeOptions): Shape {
        return when (options.shape) {
            BadgeOptions.BadgeShape.RECTANGLE -> {
                Rectangle2D.Float(0f, 0f, width.toFloat(), height.toFloat())
            }
            BadgeOptions.BadgeShape.ROUNDED_RECTANGLE -> {
                if (options.hasUniformCorners()) {
                    // Use standard rounded rectangle if all corners have the same radius
                    val radius = options.borderRadius.toFloat()
                    RoundRectangle2D.Float(0f, 0f, width.toFloat(), height.toFloat(), radius * 2, radius * 2)
                } else {
                    // Create custom shape with different corner radii
                    createCustomRoundedRectangle(
                        width.toFloat(),
                        height.toFloat(),
                        options.getCornerRadius(BadgeOptions.Corner.TOP_LEFT).toFloat() * 2,
                        options.getCornerRadius(BadgeOptions.Corner.TOP_RIGHT).toFloat() * 2,
                        options.getCornerRadius(BadgeOptions.Corner.BOTTOM_LEFT).toFloat() * 2,
                        options.getCornerRadius(BadgeOptions.Corner.BOTTOM_RIGHT).toFloat() * 2
                    )
                }
            }
            BadgeOptions.BadgeShape.PILL -> {
                val radius = height / 2f
                RoundRectangle2D.Float(0f, 0f, width.toFloat(), height.toFloat(), radius * 2, radius * 2)
            }
            BadgeOptions.BadgeShape.CIRCLE -> {
                Ellipse2D.Float(0f, 0f, width.toFloat(), height.toFloat())
            }
            BadgeOptions.BadgeShape.TRIANGLE -> {
                val path = Path2D.Float()
                path.moveTo(width / 2f, 0f)
                path.lineTo(width.toFloat(), height.toFloat())
                path.lineTo(0f, height.toFloat())
                path.closePath()
                path
            }
        }
    }

    /**
     * Create a rounded rectangle with different corner radii for each corner.
     * This creates a proper transparent shape with the specified corner radii.
     */
    private fun createCustomRoundedRectangle(
        width: Float,
        height: Float,
        topLeftRadius: Float,
        topRightRadius: Float,
        bottomLeftRadius: Float,
        bottomRightRadius: Float
    ): Shape {
        val path = Path2D.Float()

        // Start from top-left corner
        path.moveTo(topLeftRadius / 2, 0f)

        // Top edge and top-right corner
        path.lineTo(width - topRightRadius / 2, 0f)
        if (topRightRadius > 0) {
            path.quadTo(width, 0f, width, topRightRadius / 2)
        } else {
            path.lineTo(width, 0f)
        }

        // Right edge and bottom-right corner
        path.lineTo(width, height - bottomRightRadius / 2)
        if (bottomRightRadius > 0) {
            path.quadTo(width, height, width - bottomRightRadius / 2, height)
        } else {
            path.lineTo(width, height)
        }

        // Bottom edge and bottom-left corner
        path.lineTo(bottomLeftRadius / 2, height)
        if (bottomLeftRadius > 0) {
            path.quadTo(0f, height, 0f, height - bottomLeftRadius / 2)
        } else {
            path.lineTo(0f, height)
        }

        // Left edge and top-left corner
        path.lineTo(0f, topLeftRadius / 2)
        if (topLeftRadius > 0) {
            path.quadTo(0f, 0f, topLeftRadius / 2, 0f)
        } else {
            path.lineTo(0f, 0f)
        }

        path.closePath()
        return path
    }

    /**
     * Generate a cache key for a badge based on its options.
     */
    private fun generateBadgeCacheKey(options: BadgeOptions): String {
        return buildString {
            append(options.text)
            append("|")
            append(options.backgroundColor.rgb)
            append("|")
            append(options.textColor.rgb)
            append("|")
            append(options.fontSize)
            append("|")
            append(options.shape)
            append("|")
            append(options.borderRadius)
            append("|")
            if (options.borderColor != null) append(options.borderColor.rgb)
            append("|")
            append(options.borderWidth)
            append("|")
            append(options.useGradient)
            append("|")
            if (options.gradientEndColor != null) append(options.gradientEndColor.rgb)
            append("|")
            append(options.paddingX)
            append("|")
            append(options.paddingY)
        }
    }

    /**
     * Create a deep copy of a BufferedImage.
     */
    private fun copyImage(source: BufferedImage): BufferedImage {
        val copy = BufferedImage(source.width, source.height, source.type)
        val g = copy.createGraphics()
        g.drawImage(source, 0, 0, null)
        g.dispose()
        return copy
    }

    /**
     * Load a font from a file.
     */
    private fun loadFont(file: File): Font {
        val path = file.absolutePath

        // Check cache first
        fontCache[path]?.let { return it }

        try {
            val font = Font.createFont(Font.TRUETYPE_FONT, file)
            val derivedFont = font.deriveFont(Font.PLAIN, 28f)
            fontCache[path] = derivedFont
            return derivedFont
        } catch (e: IOException) {
            logger.error("Error loading font: $path", e)
            return Font("Dialog", Font.BOLD, 28)
        } catch (e: FontFormatException) {
            logger.error("Invalid font format: $path", e)
            return Font("Dialog", Font.BOLD, 28)
        }
    }

    /**
     * Load the default font from the plugin resources.
     */
    fun loadDefaultFont(): Font {
        val resourcePath = "/fonts/Roboto-Bold.ttf"
        val fontStream = javaClass.getResourceAsStream(resourcePath)

        return if (fontStream != null) {
            try {
                val font = Font.createFont(Font.TRUETYPE_FONT, fontStream)
                font.deriveFont(Font.PLAIN, 28f)
            } catch (e: Exception) {
                logger.error("Failed to load default font", e)
                Font("Dialog", Font.BOLD, 28)
            } finally {
                try {
                    fontStream.close()
                } catch (e: IOException) {
                    logger.warn("Error closing font stream", e)
                }
            }
        } else {
            logger.error("Default font resource not found: $resourcePath")
            Font("Dialog", Font.BOLD, 28)
        }
    }

    /**
     * Clear all caches.
     */
    fun clearCaches() {
        badgeCache.clear()
    }

    /**
     * Helper class to store font metrics with useful measurements.
     */
    private data class FontMetrics(
        val width: Int,
        val height: Int,
        val ascent: Int
    )

    /**
     * Get font metrics for the given font and text.
     */
    private fun getFontMetrics(font: Font, text: String): FontMetrics {
        val frc = FontRenderContext(null, true, true)
        val textLayout = TextLayout(text, font, frc)
        val bounds: Rectangle2D = textLayout.bounds

        return FontMetrics(
            width = bounds.width.toInt(),
            height = bounds.height.toInt(),
            ascent = textLayout.ascent.toInt()
        )
    }

    /**
     * Analyze a project to determine the appropriate badge template.
     */
    fun analyzeProjectForBadgeTemplate(project: Project): String {
        val projectPath = project.basePath ?: return "Default"

        // Look for common project files that indicate project type
        val projectFile = File(projectPath)
        if (!projectFile.exists() || !projectFile.isDirectory) {
            return "Default"
        }

        // Check for mobile app projects
        val hasAndroidFiles = File(projectPath, "AndroidManifest.xml").exists() ||
                File(projectPath, "app/src/main/AndroidManifest.xml").exists()
        val hasIosFiles = File(projectPath, "Info.plist").exists() ||
                File(projectPath, "AppDelegate.swift").exists()

        if (hasAndroidFiles || hasIosFiles) {
            // Try to detect environment for mobile apps
            val lowerPath = projectPath.lowercase()
            return when {
                lowerPath.contains("debug") -> "Development"
                lowerPath.contains("alpha") -> "Alpha"
                lowerPath.contains("beta") -> "Beta"
                lowerPath.contains("staging") -> "Staging"
                lowerPath.contains("test") -> "Testing"
                else -> "Development"
            }
        }

        // Check for web projects
        val hasWebFiles = File(projectPath, "package.json").exists() ||
                File(projectPath, "webpack.config.js").exists()

        if (hasWebFiles) {
            return "Version Tag"
        }

        return "Default"
    }

    companion object {
        private const val MAX_CACHE_PIXELS = 1024 * 1024 // 1M pixels max for cached badges
    }
}