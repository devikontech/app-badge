package org.devikon.app.badge.listeners

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import org.devikon.app.badge.model.BadgeOptions
import org.devikon.app.badge.model.BadgePositionCalculator
import org.devikon.app.badge.services.BadgeService
import org.devikon.app.badge.services.ImageService
import java.awt.AlphaComposite
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
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseMotionAdapter
import java.awt.geom.Path2D
import java.awt.image.BufferedImage
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.JPanel
import javax.swing.Timer

/**
 * Advanced preview component for badges with interactive positioning and high-quality rendering.
 */
class BadgePreviewComponent : JPanel() {
    private val logger = Logger.getInstance(BadgePreviewComponent::class.java)
    private val badgeService = service<BadgeService>()
    private val imageService = service<ImageService>()

    // Preview state
    private var badgeOptions: BadgeOptions = BadgeOptions("PREVIEW")
    private var targetImage: BufferedImage? = null
    private var targetImageFile: File? = null
    private var previewImage: BufferedImage? = null
    private var selectedArea: Rectangle? = null

    // Interactive state
    private var isDragging = false
    private var dragStart = Point()
    private var currentPosition: BadgeOptions.Position? = null
    private var badgeRect: Rectangle? = null
    private var isOver = false

    // Performance
    private val updateScheduled = AtomicBoolean(false)
    private val updateTimer = Timer(100) { regeneratePreview() }
    private var lastRenderTime = 0L

    // Animations
    private var fadeInProgress = 0.0

    private val fadeTimer: Timer = Timer(30) {
        fadeInProgress = (fadeInProgress + 0.1).coerceAtMost(1.0)
        repaint()
        if (fadeInProgress >= 1.0) {
            fadeTimer.stop()
        }
    }

    // Visual design elements
    private val gridColor = JBColor(Color(200, 200, 200, 40), Color(50, 50, 50, 40))
    private val selectedAreaColor = JBColor(Color(100, 130, 255, 80), Color(100, 150, 255, 100))
    private val loadingMessage = "Generating preview..."

    init {
        // Basic setup
        isOpaque = false
        isFocusable = true
        preferredSize = Dimension(300, 200)
        border = JBUI.Borders.empty(5)

        // Default sample background
        targetImage = createDefaultPreviewImage()

        // Mouse interaction listeners
        setupMouseInteraction()

        // Resize listener
        addComponentListener(object : ComponentAdapter() {
            override fun componentResized(e: ComponentEvent) {
                if (width > 0 && height > 0) {
                    schedulePreviewUpdate()
                }
            }
        })

        // Timer setup
        updateTimer.isRepeats = false

        // Initial generation
        schedulePreviewUpdate()
    }

    /**
     * Set up mouse interaction for drag and drop positioning.
     */
    private fun setupMouseInteraction() {
        addMouseListener(object : MouseAdapter() {
            override fun mouseEntered(e: MouseEvent) {
                isOver = true
                repaint()
            }

            override fun mouseExited(e: MouseEvent) {
                isOver = false
                repaint()
            }

            override fun mousePressed(e: MouseEvent) {
                val rect = badgeRect
                if (rect != null && rect.contains(e.point)) {
                    isDragging = true
                    dragStart = e.point

                    // Calculate initial position percentage
                    val img = previewImage ?: return
                    val x = ((e.x.toDouble() / img.width) * 100).toInt().coerceIn(0, 100)
                    val y = ((e.y.toDouble() / img.height) * 100).toInt().coerceIn(0, 100)

                    currentPosition = BadgeOptions.Position(x, y)

                    // Highlight the area
                    selectedArea = Rectangle(rect)
                    repaint()
                }
            }

            override fun mouseReleased(e: MouseEvent) {
                if (isDragging) {
                    isDragging = false

                    // Update with final position
                    val img = previewImage ?: return
                    val x = ((e.x.toDouble() / img.width) * 100).toInt().coerceIn(0, 100)
                    val y = ((e.y.toDouble() / img.height) * 100).toInt().coerceIn(0, 100)

                    currentPosition = BadgeOptions.Position(x, y)
                    badgeOptions = badgeOptions.copy(position = currentPosition)

                    // Clear selection area
                    selectedArea = null

                    // Regenerate preview with new position
                    schedulePreviewUpdate()
                }
            }
        })

        addMouseMotionListener(object : MouseMotionAdapter() {
            override fun mouseMoved(e: MouseEvent) {
                val rect = badgeRect
                if (rect != null && rect.contains(e.point)) {
                    cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                } else {
                    cursor = Cursor.getDefaultCursor()
                }
            }

            override fun mouseDragged(e: MouseEvent) {
                if (isDragging) {
                    val img = previewImage ?: return

                    // Calculate new position in percentage
                    val x = ((e.x.toDouble() / img.width) * 100).toInt().coerceIn(0, 100)
                    val y = ((e.y.toDouble() / img.height) * 100).toInt().coerceIn(0, 100)

                    if (currentPosition?.x != x || currentPosition?.y != y) {
                        currentPosition = BadgeOptions.Position(x, y)
                        badgeOptions = badgeOptions.copy(position = currentPosition)

                        // Update selection area
                        val rect = badgeRect
                        if (rect != null) {
                            val dx = e.x - dragStart.x
                            val dy = e.y - dragStart.y
                            selectedArea = Rectangle(
                                rect.x + dx,
                                rect.y + dy,
                                rect.width,
                                rect.height
                            )
                        }

                        // Force repaint for immediate feedback
                        repaint()

                        // Schedule update of actual preview
                        schedulePreviewUpdate()
                    }
                }
            }
        })
    }

    /**
     * Update the badge options for the preview.
     */
    fun updateOptions(options: BadgeOptions) {
        if (badgeOptions != options) {
            // If position was manually set, preserve it
            val newOptions = if (currentPosition != null) {
                options.copy(position = currentPosition)
            } else {
                options
            }

            badgeOptions = newOptions
            schedulePreviewUpdate()
        }
    }

    /**
     * Set a target image file for the preview.
     */
    fun setTargetImageFile(file: File?) {
        if (targetImageFile != file) {
            targetImageFile = file
            targetImage = null

            // Clear cache
            previewImage = null

            // Reset position
            currentPosition = null

            schedulePreviewUpdate()
        }
    }

    /**
     * Set a target image for the preview.
     */
    fun setTargetImage(image: BufferedImage?) {
        if (image != null && targetImage != image) {
            targetImage = image
            targetImageFile = null

            // Clear cache
            previewImage = null

            // Reset position
            currentPosition = null

            schedulePreviewUpdate()
        }
    }

    /**
     * Get the current badge position if manually positioned.
     */
    fun getCurrentPosition(): BadgeOptions.Position? {
        return currentPosition
    }

    /**
     * Schedule a preview update with debouncing.
     */
    private fun schedulePreviewUpdate() {
        if (updateScheduled.compareAndSet(false, true)) {
            updateTimer.restart()
        }
    }

    /**
     * Regenerate the preview image.
     */
    private fun regeneratePreview() {
        updateScheduled.set(false)

        // Use a background thread for heavy image processing
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                // Load or use existing image
                var sourceImage = targetImage
                if (sourceImage == null && targetImageFile != null) {
                    sourceImage = imageService.loadImage(targetImageFile!!)
                }

                if (sourceImage == null) {
                    // Create default image if no source
                    sourceImage = createDefaultPreviewImage()
                }

                // Scale down large images for preview
                val startTime = System.currentTimeMillis()

                val scaledImage = fitImageToPreviewSize(sourceImage)

                // Generate badge preview
                val result = badgeService.addBadgeToImage(scaledImage, badgeOptions)

                // Calculate badge rectangle for interaction
                calculateBadgeRectangle(result)

                // Measure performance
                lastRenderTime = System.currentTimeMillis() - startTime

                // Start fade-in animation
                fadeInProgress = 0.0
                fadeTimer.start()

                // Update UI on EDT
                ApplicationManager.getApplication().invokeLater {
                    previewImage = result
                    repaint()
                }
            } catch (e: Exception) {
                logger.error("Error generating preview", e)
            }
        }
    }

    /**
     * Calculate the badge rectangle for mouse interaction.
     */
    private fun calculateBadgeRectangle(previewImage: BufferedImage) {
        // This is an approximation - in a real implementation we would
        // need to do more sophisticated detection of where the badge actually is
        val text = badgeOptions.text
        val fontSize = badgeOptions.fontSize
        val badgeWidth = text.length * fontSize / 2 + badgeOptions.paddingX * 2
        val badgeHeight = fontSize + badgeOptions.paddingY * 2

        val pos = currentPosition
        if (pos != null) {
            // Calculate from manual position
            val x = (previewImage.width * pos.x / 100) - badgeWidth / 2
            val y = (previewImage.height * (pos.y ?: pos.x) / 100) - badgeHeight / 2

            badgeRect = Rectangle(x, y, badgeWidth, badgeHeight)
        } else {
            // Calculate based on gravity
            val containerWidth = previewImage.width
            val containerHeight = previewImage.height

            // Estimate inset width
            val insetWidth = containerWidth -
                    imageService.findInset(previewImage, ImageService.Direction.EAST) -
                    imageService.findInset(previewImage, ImageService.Direction.WEST)

            val radius = insetWidth / 2
            val gravity = badgeOptions.gravity

            val position = BadgePositionCalculator.calculateCircularBadgePosition(
                containerWidth,
                containerHeight,
                badgeWidth,
                badgeHeight,
                radius,
                gravity
            )

            badgeRect = Rectangle(
                position.point.x,
                position.point.y,
                badgeWidth,
                badgeHeight
            )
        }
    }

    /**
     * Scale an image to fit the preview size.
     */
    private fun fitImageToPreviewSize(image: BufferedImage): BufferedImage {
        val maxWidth = width.coerceAtLeast(1)
        val maxHeight = height.coerceAtLeast(1)

        if (image.width <= maxWidth && image.height <= maxHeight) {
            return image
        }

        val widthRatio = maxWidth.toDouble() / image.width
        val heightRatio = maxHeight.toDouble() / image.height
        val scale = Math.min(widthRatio, heightRatio)

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
     * Create a default preview image.
     */
    private fun createDefaultPreviewImage(): BufferedImage {
        val size = 192
        val image = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()

        // Apply quality settings
        g.setRenderingHint(
            RenderingHints.KEY_ANTIALIASING,
            RenderingHints.VALUE_ANTIALIAS_ON
        )
        g.setRenderingHint(
            RenderingHints.KEY_RENDERING,
            RenderingHints.VALUE_RENDER_QUALITY
        )

        // Create a nice gradient background
        val gradient = GradientPaint(
            0f, 0f, JBColor(Color(30, 120, 220), Color(40, 80, 160)),
            size.toFloat(), size.toFloat(), JBColor(Color(20, 80, 160), Color(20, 40, 100))
        )
        g.paint = gradient
        g.fillRoundRect(0, 0, size, size, 30, 30)

        // Add a simple app icon design
        g.color = JBColor(Color(255, 255, 255, 180), Color(255, 255, 255, 180))
        g.fillOval(size/4, size/4, size/2, size/2)

        g.color = JBColor(Color(255, 255, 255, 40), Color(255, 255, 255, 40))
        g.fillOval(size/3, size/3, size/3, size/3)

        g.dispose()
        return image
    }

    /**
     * Paint the preview component.
     */
    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        val g2d = g as Graphics2D

        // Apply quality settings
        g2d.setRenderingHint(
            RenderingHints.KEY_ANTIALIASING,
            RenderingHints.VALUE_ANTIALIAS_ON
        )
        g2d.setRenderingHint(
            RenderingHints.KEY_INTERPOLATION,
            RenderingHints.VALUE_INTERPOLATION_BILINEAR
        )

        val preview = previewImage

        if (preview != null) {
            // Calculate centered position
            val x = (width - preview.width) / 2
            val y = (height - preview.height) / 2

            // Draw subtle grid in background for visual reference
            drawGrid(g2d, x, y, preview.width, preview.height)

            // Draw the image with fade effect if animating
            val origComposite = g2d.composite
            if (fadeInProgress < 1.0) {
                g2d.composite = AlphaComposite.getInstance(
                    AlphaComposite.SRC_OVER,
                    fadeInProgress.toFloat()
                )
            }

            g2d.drawImage(preview, x, y, null)
            g2d.composite = origComposite

            // Draw interaction elements
            if (isOver || isDragging) {
                drawBadgeHighlight(g2d, x, y)
            }

            // Draw selection area if dragging
            val selArea = selectedArea
            if (isDragging && selArea != null) {
                g2d.color = selectedAreaColor
                g2d.stroke = BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)

                // Draw based on badge shape
                val offsetRect = Rectangle(selArea.x + x, selArea.y + y, selArea.width, selArea.height)
                drawShapedHighlight(g2d, offsetRect, badgeOptions.shape)

                // Draw drag handle
                g2d.fillOval(
                    offsetRect.x + offsetRect.width/2 - 3,
                    offsetRect.y + offsetRect.height/2 - 3,
                    6, 6
                )
            }

            // Draw performance stats in debug mode (useful during development)
            if (false) { // Set to true for debugging
                g2d.color = JBColor.GRAY
                g2d.font = UIUtil.getFont(UIUtil.FontSize.SMALL, UIUtil.getTitledBorderFont())
                g2d.drawString("Render: ${lastRenderTime}ms", 5, height - 5)
            }
        } else {
            // Draw loading indicator
            drawLoadingIndicator(g2d)
        }
    }

    /**
     * Draw a grid in the background.
     */
    private fun drawGrid(g2d: Graphics2D, x: Int, y: Int, width: Int, height: Int) {
        g2d.color = gridColor
        g2d.stroke = BasicStroke(0.5f)

        // Draw grid lines
        val gridSize = 20
        for (i in 0..width step gridSize) {
            g2d.drawLine(x + i, y, x + i, y + height)
        }

        for (i in 0..height step gridSize) {
            g2d.drawLine(x, y + i, x + width, y + i)
        }

        // Draw border
        g2d.drawRect(x, y, width, height)
    }

    /**
     * Draw a highlight around the badge area.
     */
    private fun drawBadgeHighlight(g2d: Graphics2D, offsetX: Int, offsetY: Int) {
        val rect = badgeRect ?: return

        // Adjust for panel offset
        val offsetRect = Rectangle(
            rect.x + offsetX,
            rect.y + offsetY,
            rect.width,
            rect.height
        )

        // Draw a shape-appropriate highlight
        g2d.color = JBColor(Color(100, 100, 255, 80), Color(100, 100, 255, 120))
        g2d.stroke = BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)

        drawShapedHighlight(g2d, offsetRect, badgeOptions.shape)
    }

    /**
     * Draw a highlight that matches the badge shape.
     */
    private fun drawShapedHighlight(g2d: Graphics2D, rect: Rectangle, shape: BadgeOptions.BadgeShape) {
        when (shape) {
            BadgeOptions.BadgeShape.RECTANGLE -> {
                g2d.drawRect(rect.x, rect.y, rect.width, rect.height)
            }
            BadgeOptions.BadgeShape.ROUNDED_RECTANGLE -> {
                val radius = badgeOptions.borderRadius * 2
                g2d.drawRoundRect(
                    rect.x, rect.y, rect.width, rect.height,
                    radius, radius
                )
            }
            BadgeOptions.BadgeShape.PILL -> {
                val radius = rect.height
                g2d.drawRoundRect(
                    rect.x, rect.y, rect.width, rect.height,
                    radius, radius
                )
            }
            BadgeOptions.BadgeShape.CIRCLE -> {
                val diameter = Math.max(rect.width, rect.height)
                g2d.drawOval(
                    rect.x, rect.y, diameter, diameter
                )
            }
            BadgeOptions.BadgeShape.TRIANGLE -> {
                val path = Path2D.Float()
                path.moveTo(rect.x + rect.width/2.0, rect.y.toDouble())
                path.lineTo(rect.x + rect.width.toDouble(), rect.y + rect.height.toDouble())
                path.lineTo(rect.x.toDouble(), rect.y + rect.height.toDouble())
                path.closePath()
                g2d.draw(path)
            }
        }
    }

    /**
     * Draw a loading indicator when generating preview.
     */
    private fun drawLoadingIndicator(g2d: Graphics2D) {
        // Draw text
        val fm = g2d.fontMetrics
        val messageWidth = fm.stringWidth(loadingMessage)

        g2d.color = UIUtil.getLabelForeground()
        g2d.drawString(
            loadingMessage,
            (width - messageWidth) / 2,
            height / 2
        )

        // Draw spinner animation
        val spinnerSize = 16
        val spinnerX = (width - spinnerSize) / 2
        val spinnerY = height / 2 + 15

        val angle = (System.currentTimeMillis() / 15) % 360
        g2d.rotate(Math.toRadians(angle.toDouble()), spinnerX + spinnerSize/2.0, spinnerY + spinnerSize/2.0)

        g2d.stroke = BasicStroke(2f)
        g2d.color = JBColor.GRAY
        g2d.drawArc(spinnerX, spinnerY, spinnerSize, spinnerSize, 0, 270)
    }
}