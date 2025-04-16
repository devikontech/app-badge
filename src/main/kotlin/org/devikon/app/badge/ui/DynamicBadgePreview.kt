package org.devikon.app.badge.ui


import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import org.devikon.app.badge.model.BadgeOptions
import org.devikon.app.badge.model.BadgePositionCalculator
import org.devikon.app.badge.services.BadgeService
import org.devikon.app.badge.services.ImageService
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Cursor
import java.awt.Dimension
import java.awt.GradientPaint
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Point
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseMotionAdapter
import java.awt.image.BufferedImage
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.JPanel
import javax.swing.SwingUtilities
import javax.swing.Timer
import kotlin.math.min

/**
 * Dynamic preview panel for showing how the badge will look on the actual image.
 * Includes interactive badge positioning and live updates.
 */
class DynamicBadgePreview(
    private val targetImage: BufferedImage? = null,
    private val targetImageFile: File? = null
) : JPanel() {
    private val logger = Logger.getInstance(DynamicBadgePreview::class.java)
    private val badgeService = service<BadgeService>()
    private val imageService = service<ImageService>()

    // Current badge options
    private var badgeOptions = BadgeOptions("PREVIEW")

    // Cached generated preview
    private var previewImage: BufferedImage? = null

    // For manual badge positioning
    private var isDragging = false
    private var dragStart = Point()
    private var currentPosition: BadgeOptions.Position? = null

    // To avoid excessive preview regeneration
    private val previewUpdateScheduled = AtomicBoolean(false)
    private val updateTimer = Timer(100) { regeneratePreview() }

    // The image to preview on if no target specified
    private val defaultPreviewImage: BufferedImage by lazy {
        createDefaultPreviewImage()
    }

    init {
        // Set minimum size
        minimumSize = Dimension(300, 200)
        preferredSize = Dimension(400, 300)
        border = JBUI.Borders.empty(10)

        // Enable mouse interaction for dragging badge
        addMouseListener(object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) {
                val previewImg = previewImage ?: return

                // Check if clicking on the badge region
                val badgeRect = getApproximateBadgeRect() ?: return
                if (badgeRect.contains(e.point)) {
                    isDragging = true
                    dragStart = e.point

                    // Calculate initial position percentage
                    val previewWidth = previewImg.width.toDouble()
                    val previewHeight = previewImg.height.toDouble()
                    val x = ((e.x - badgeRect.width / 2) / previewWidth * 100).toInt()
                    val y = ((e.y - badgeRect.height / 2) / previewHeight * 100).toInt()

                    currentPosition = BadgeOptions.Position(
                        x.coerceIn(0, 100),
                        y.coerceIn(0, 100)
                    )
                }
            }

            override fun mouseReleased(e: MouseEvent) {
                if (isDragging) {
                    isDragging = false

                    // Update with final position
                    val previewImg = previewImage ?: return
                    val x = ((e.x.toDouble() / previewImg.width) * 100).toInt().coerceIn(0, 100)
                    val y = ((e.y.toDouble() / previewImg.height) * 100).toInt().coerceIn(0, 100)

                    currentPosition = BadgeOptions.Position(x, y)
                    badgeOptions = badgeOptions.copy(position = currentPosition)

                    // Regenerate preview with new position
                    schedulePreviewUpdate()
                }
            }
        })

        addMouseMotionListener(object : MouseMotionAdapter() {
            override fun mouseDragged(e: MouseEvent) {
                if (isDragging) {
                    val previewImg = previewImage ?: return

                    // Calculate new position in percentage
                    val x = ((e.x.toDouble() / previewImg.width) * 100).toInt().coerceIn(0, 100)
                    val y = ((e.y.toDouble() / previewImg.height) * 100).toInt().coerceIn(0, 100)

                    if (currentPosition?.x != x || currentPosition?.y != y) {
                        currentPosition = BadgeOptions.Position(x, y)
                        badgeOptions = badgeOptions.copy(position = currentPosition)

                        // Update preview during drag (with throttling)
                        schedulePreviewUpdate()

                        // Force immediate repaint for visual feedback
                        repaint()
                    }
                }
            }

            override fun mouseMoved(e: MouseEvent) {
                // Update cursor if hovering over badge
                val badgeRect = getApproximateBadgeRect()
                if (badgeRect != null && badgeRect.contains(e.point)) {
                    cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                } else {
                    cursor = Cursor.getDefaultCursor()
                }
            }
        })

        // Initialize
        updateTimer.isRepeats = false
        schedulePreviewUpdate()
    }

    /**
     * Update the badge options for the preview.
     */
    fun updateBadgeOptions(options: BadgeOptions) {
        if (badgeOptions != options) {
            badgeOptions = options
            currentPosition = options.position
            schedulePreviewUpdate()
        }
    }

    /**
     * Returns the current badge position if manually positioned.
     */
    fun getCurrentPosition(): BadgeOptions.Position? {
        return currentPosition
    }

    /**
     * Schedule a preview update with debouncing.
     */
    private fun schedulePreviewUpdate() {
        if (previewUpdateScheduled.compareAndSet(false, true)) {
            updateTimer.restart()
        }
    }

    /**
     * Actually regenerate the preview.
     */
    private fun regeneratePreview() {
        previewUpdateScheduled.set(false)

        // Use a background thread for heavy image processing
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                val sourceImage = when {
                    targetImage != null -> targetImage
                    targetImageFile != null -> imageService.loadImage(targetImageFile)
                    else -> defaultPreviewImage
                }

                if (sourceImage != null) {
                    // Scale down large images for preview
                    val scaledImage = scaleForPreview(sourceImage)

                    // Generate badge preview
                    val result = badgeService.addBadgeToImage(scaledImage, badgeOptions)

                    // Update UI on EDT
                    SwingUtilities.invokeLater {
                        previewImage = result
                        repaint()
                    }
                }
            } catch (e: Exception) {
                logger.error("Error generating preview", e)
            }
        }
    }

    /**
     * Paint the preview image.
     */
    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        val g2d = g as Graphics2D

        // Apply anti-aliasing
        g2d.setRenderingHint(
            RenderingHints.KEY_ANTIALIASING,
            RenderingHints.VALUE_ANTIALIAS_ON
        )

        // Draw the preview image
        val preview = previewImage
        if (preview != null) {
            // Center the preview
            val x = (width - preview.width) / 2
            val y = (height - preview.height) / 2
            g2d.drawImage(preview, x, y, null)

            // Draw drag handles if in interactive mode
            if (currentPosition != null) {
                val badgeRect = getApproximateBadgeRect()
                if (badgeRect != null) {
                    // Draw a subtle highlight around the badge area when manually positioned
                    g2d.stroke = BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
                    g2d.color = JBColor(Color(100, 100, 255, 80), Color(100, 100, 255, 120))
                    g2d.drawRect(
                        badgeRect.x,
                        badgeRect.y,
                        badgeRect.width,
                        badgeRect.height
                    )
                }
            }
        } else {
            // Draw loading indicator
            val message = "Generating preview..."
            val fm = g2d.fontMetrics
            val messageWidth = fm.stringWidth(message)

            g2d.color = foreground
            g2d.drawString(
                message,
                (width - messageWidth) / 2,
                height / 2
            )
        }
    }

    /**
     * Scale large images to a reasonable preview size.
     */
    private fun scaleForPreview(image: BufferedImage): BufferedImage {
        val maxDimension = 500

        if (image.width <= maxDimension && image.height <= maxDimension) {
            return image
        }

        val scale = min(
            maxDimension.toDouble() / image.width,
            maxDimension.toDouble() / image.height
        )

        val newWidth = (image.width * scale).toInt()
        val newHeight = (image.height * scale).toInt()

        val scaled = BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_ARGB)
        val g = scaled.createGraphics()
        g.setRenderingHint(
            RenderingHints.KEY_INTERPOLATION,
            RenderingHints.VALUE_INTERPOLATION_BILINEAR
        )
        g.drawImage(image, 0, 0, newWidth, newHeight, null)
        g.dispose()

        return scaled
    }

    /**
     * Create a default preview image if no target is provided.
     */
    private fun createDefaultPreviewImage(): BufferedImage {
        val size = 192
        val image = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()

        // Create a simple app icon
        g.setRenderingHint(
            RenderingHints.KEY_ANTIALIASING,
            RenderingHints.VALUE_ANTIALIAS_ON
        )

        // Fill background with a gradient
        val gradient = GradientPaint(
            0f, 0f, Color(30, 120, 220),
            size.toFloat(), size.toFloat(), Color(20, 80, 160)
        )
        g.paint = gradient
        g.fillRoundRect(0, 0, size, size, 30, 30)

        // Add a simple icon
        g.color = Color(255, 255, 255, 180)
        g.fillOval(size/4, size/4, size/2, size/2)

        g.dispose()
        return image
    }

    /**
     * Calculate an approximate rectangle for the badge in the current preview.
     */
    private fun getApproximateBadgeRect(): Rectangle? {
        val preview = previewImage ?: return null

        // Estimate badge size based on font size and padding
        val textLength = badgeOptions.text.length
        val estWidth = textLength * badgeOptions.fontSize / 2 + badgeOptions.paddingX * 2
        val estHeight = badgeOptions.fontSize + badgeOptions.paddingY * 2

        // Calculate position based on current options
        val pos = currentPosition
        if (pos != null) {
            // Position is specified by percentage
            val x = (preview.width * pos.x / 100) - estWidth / 2
            val y = (preview.height * (pos.y ?: pos.x) / 100) - estHeight / 2

            return Rectangle(x, y, estWidth, estHeight)
        } else {
            // Use gravity and calculate approximate position
            val containerWidth = preview.width
            val containerHeight = preview.height
            val insetWidth = containerWidth -
                    imageService.findInset(preview, ImageService.Direction.EAST) -
                    imageService.findInset(preview, ImageService.Direction.WEST)

            val radius = insetWidth / 2

            val position = BadgePositionCalculator.calculateCircularBadgePosition(
                containerWidth,
                containerHeight,
                estWidth,
                estHeight,
                radius,
                badgeOptions.gravity
            )

            return Rectangle(
                position.point.x,
                position.point.y,
                estWidth,
                estHeight
            )
        }
    }
}