package org.devikon.app.badge.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.concurrency.AppExecutorUtil
import org.imgscalr.Scalr
import java.awt.AlphaComposite
import java.awt.Color
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.geom.AffineTransform
import java.awt.image.BufferedImage
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Future
import javax.imageio.ImageIO
import kotlin.math.round

/**
 * Enhanced service for image processing operations with performance optimizations.
 */
@Service
class ImageService {
    private val logger = Logger.getInstance(ImageService::class.java)

    // Image cache to avoid reloading the same images
    private val imageCache = ConcurrentHashMap<String, BufferedImage>()

    // Shadow cache to avoid regenerating similar shadows
    private val shadowCache = ConcurrentHashMap<String, BufferedImage>()

    // Custom thread pool for concurrent image processing
    private val imageProcessorPool = AppExecutorUtil.createBoundedApplicationPoolExecutor(
        "Badge Image Processor",
        Runtime.getRuntime().availableProcessors()
    )

    /**
     * Load an image from disk with caching.
     */
    fun loadImage(file: File): BufferedImage? {
        val path = file.absolutePath

        // Check cache first
        val cachedImage = imageCache[path]

        // TODO:: Check this
        if (cachedImage != null) {
            return cachedImage.copyData(null) as BufferedImage?
        }

        return try {
            val image = ImageIO.read(file)
            if (image != null) {
                // Only cache smaller images to prevent memory issues
                if (file.length() < MAX_CACHE_SIZE_BYTES) {
                    imageCache[path] = image
                }
            }
            image
        } catch (e: IOException) {
            logger.error("Error loading image: ${file.path}", e)
            null
        }
    }

    /**
     * Save an image to disk.
     */
    fun saveImage(image: BufferedImage, file: File): Boolean {
        return try {
            // Ensure parent directory exists
            file.parentFile?.mkdirs()

            // Enable better PNG compression
            val format = file.extension.lowercase()

            // Use a different compression strategy depending on the format
            if (format == "png") {
                val params = javax.imageio.ImageWriteParam(null)
                params.compressionMode = javax.imageio.ImageWriteParam.MODE_EXPLICIT
                params.compressionQuality = 0.9f

                val ios = javax.imageio.ImageIO.createImageOutputStream(file)
                val writer = javax.imageio.ImageIO.getImageWritersByFormatName("png").next()
                writer.output = ios
                writer.write(null, javax.imageio.IIOImage(image, null, null), params)
                writer.dispose()
                ios.close()
                true
            } else {
                ImageIO.write(image, format, file)
                true
            }
        } catch (e: IOException) {
            logger.error("Error saving image: ${file.path}", e)
            false
        }
    }

    /**
     * Create a transparent image of the specified dimensions.
     */
    fun createTransparentImage(width: Int, height: Int): BufferedImage {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val g2d = image.createGraphics()

        // Apply best rendering hints for quality
        applyRenderingHints(g2d)

        g2d.composite = AlphaComposite.Clear
        g2d.fillRect(0, 0, width, height)
        g2d.dispose()
        return image
    }

    /**
     * Apply optimal rendering hints to graphics context.
     */
    private fun applyRenderingHints(g2d: Graphics2D) {
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        g2d.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY)
        g2d.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_QUALITY)
    }

    /**
     * Rotate an image by the specified angle in degrees with optimized algorithm.
     */
    fun rotateImage(image: BufferedImage, degrees: Int): BufferedImage {
        if (degrees == 0) return image

        val radians = Math.toRadians(degrees.toDouble())
        val sin = Math.abs(Math.sin(radians))
        val cos = Math.abs(Math.cos(radians))

        val width = image.width
        val height = image.height

        val newWidth = round(width * cos + height * sin).toInt()
        val newHeight = round(height * cos + width * sin).toInt()

        val rotated = BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_ARGB)
        val g2d = rotated.createGraphics()
        applyRenderingHints(g2d)

        // Set to transparent
        g2d.composite = AlphaComposite.Clear
        g2d.fillRect(0, 0, newWidth, newHeight)
        g2d.composite = AlphaComposite.SrcOver

        // Apply transformation
        val at = AffineTransform()
        at.translate((newWidth - width) / 2.0, (newHeight - height) / 2.0)
        at.rotate(radians, width / 2.0, height / 2.0)
        g2d.transform = at

        g2d.drawImage(image, 0, 0, null)
        g2d.dispose()

        return rotated
    }

    /**
     * Adds a shadow effect to an image with caching for similar shadow parameters.
     */
    fun addShadow(image: BufferedImage, shadowColor: Color, blurRadius: Int): BufferedImage {
        // Create cache key based on shadow parameters and image dimensions
        val cacheKey = "${image.width}x${image.height}:${shadowColor.rgb}:$blurRadius"

        // Create a larger canvas to accommodate the shadow
        val width = image.width + blurRadius * 2
        val height = image.height + blurRadius * 2
        val result = createTransparentImage(width, height)

        // Check if we have a cached shadow for similar parameters
        val shadow = if (shadowCache.containsKey(cacheKey)) {
            shadowCache[cacheKey]!!
        } else {
            // Generate new shadow
            val newShadow = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
            val gShadow = newShadow.createGraphics()
            gShadow.color = shadowColor

            // Create an optimization by using an alpha mask approach
            val alphaMask = BufferedImage(image.width, image.height, BufferedImage.TYPE_BYTE_GRAY)
            val amg = alphaMask.createGraphics()

            // Extract alpha channel from original image
            for (y in 0 until image.height) {
                for (x in 0 until image.width) {
                    val argb = image.getRGB(x, y)
                    val alpha = (argb shr 24) and 0xFF
                    if (alpha > 0) {
                        amg.color = Color(alpha, alpha, alpha)
                        amg.fillRect(x, y, 1, 1)
                    }
                }
            }
            amg.dispose()

            // Draw using the alpha mask
            gShadow.drawImage(alphaMask, blurRadius, blurRadius, null)
            gShadow.dispose()

            // Apply optimized blur
            val blurred = applyOptimizedBlur(newShadow, blurRadius)

            // Cache for future use (only for reasonable sizes)
            if (width * height < MAX_SHADOW_CACHE_PIXELS) {
                shadowCache[cacheKey] = blurred
            }

            blurred
        }

        // Composite the shadow and the original image
        val g = result.createGraphics()
        applyRenderingHints(g)
        g.drawImage(shadow, 0, 0, null)
        g.drawImage(image, blurRadius, blurRadius, null)
        g.dispose()

        return result
    }

    /**
     * Apply optimized blur for shadow generation.
     */
    private fun applyOptimizedBlur(image: BufferedImage, radius: Int): BufferedImage {
        // Use a multi-pass box blur for better performance than Gaussian blur
        var result = image

        // Adjust passes based on radius for optimal quality/performance trade-off
        val passes = when {
            radius <= 2 -> 2
            radius <= 10 -> 3
            else -> 4
        }

        for (i in 0 until passes) {
            result = Scalr.apply(result, Scalr.OP_ANTIALIAS)
        }

        return result
    }

    /**
     * Find the inset from the edge to the first non-transparent pixel.
     * Optimized with early exit conditions and scan skipping.
     */
    fun findInset(image: BufferedImage, direction: Direction): Int {
        val centerX = image.width / 2
        val centerY = image.height / 2

        // Skip factor for faster scanning (only check every nth pixel initially)
        val initialSkip = Math.max(1, Math.min(image.width, image.height) / 50)

        when (direction) {
            Direction.NORTH -> {
                // Fast scan with skipping
                var lastY = 0
                for (y in 0 until centerY step initialSkip) {
                    val pixel = image.getRGB(centerX, y)
                    val alpha = (pixel shr 24) and 0xFF
                    if (alpha > ALPHA_THRESHOLD) {
                        lastY = y
                        break
                    }
                }

                // Detailed scan from the last skip point
                for (y in Math.max(0, lastY - initialSkip) until lastY + 1) {
                    val pixel = image.getRGB(centerX, y)
                    val alpha = (pixel shr 24) and 0xFF
                    if (alpha > ALPHA_THRESHOLD) {
                        return y
                    }
                }
            }

            Direction.SOUTH -> {
                var lastY = image.height - 1
                for (y in image.height - 1 downTo centerY step initialSkip) {
                    val pixel = image.getRGB(centerX, y)
                    val alpha = (pixel shr 24) and 0xFF
                    if (alpha > ALPHA_THRESHOLD) {
                        lastY = y
                        break
                    }
                }

                for (y in Math.min(image.height - 1, lastY + initialSkip) downTo lastY) {
                    val pixel = image.getRGB(centerX, y)
                    val alpha = (pixel shr 24) and 0xFF
                    if (alpha > ALPHA_THRESHOLD) {
                        return image.height - 1 - y
                    }
                }
            }

            Direction.EAST -> {
                var lastX = 0
                for (x in 0 until centerX step initialSkip) {
                    val pixel = image.getRGB(x, centerY)
                    val alpha = (pixel shr 24) and 0xFF
                    if (alpha > ALPHA_THRESHOLD) {
                        lastX = x
                        break
                    }
                }

                for (x in Math.max(0, lastX - initialSkip) until lastX + 1) {
                    val pixel = image.getRGB(x, centerY)
                    val alpha = (pixel shr 24) and 0xFF
                    if (alpha > ALPHA_THRESHOLD) {
                        return x
                    }
                }
            }

            Direction.WEST -> {
                var lastX = image.width - 1
                for (x in image.width - 1 downTo centerX step initialSkip) {
                    val pixel = image.getRGB(x, centerY)
                    val alpha = (pixel shr 24) and 0xFF
                    if (alpha > ALPHA_THRESHOLD) {
                        lastX = x
                        break
                    }
                }

                for (x in Math.min(image.width - 1, lastX + initialSkip) downTo lastX) {
                    val pixel = image.getRGB(x, centerY)
                    val alpha = (pixel shr 24) and 0xFF
                    if (alpha > ALPHA_THRESHOLD) {
                        return image.width - 1 - x
                    }
                }
            }
        }

        // Default to 0 if no inset found
        return 0
    }

    /**
     * Process multiple images asynchronously.
     */
    fun processImagesAsync(
        images: List<Pair<File, BufferedImage?>>,
        processor: (BufferedImage) -> BufferedImage
    ): List<Future<Boolean>> {
        return images.map { (file, imageNullable) ->
            imageProcessorPool.submit<Boolean> {
                val image = imageNullable ?: loadImage(file) ?: return@submit false
                val processed = processor(image)
                saveImage(processed, file)
            }
        }
    }

    /**
     * Clear all caches.
     */
    fun clearCaches() {
        imageCache.clear()
        shadowCache.clear()
    }

    /**
     * Direction enum for inset calculations
     */
    enum class Direction {
        NORTH, SOUTH, EAST, WEST
    }

    companion object {
        // Constants for caching limits
        private const val MAX_CACHE_SIZE_BYTES = 1024 * 1024 * 5 // 5MB limit per cached image
        private const val MAX_SHADOW_CACHE_PIXELS = 1920 * 1080 // Roughly 2M pixels
        private const val ALPHA_THRESHOLD = 20 // Alpha threshold for edge detection
    }
}