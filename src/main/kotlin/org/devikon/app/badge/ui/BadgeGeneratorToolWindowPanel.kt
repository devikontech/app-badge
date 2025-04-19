package org.devikon.app.badge.ui

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.ColorPanel
import com.intellij.ui.JBSplitter
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.MutableProperty
import com.intellij.ui.dsl.builder.bindIntValue
import com.intellij.ui.dsl.builder.bindItem
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.layout.ComponentPredicate
import com.intellij.util.ui.JBUI
import org.devikon.app.badge.integrations.ProjectIntegrations
import org.devikon.app.badge.model.BadgeGravity
import org.devikon.app.badge.model.BadgeOptions
import org.devikon.app.badge.services.BadgeService
import org.devikon.app.badge.services.ImageService
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.io.File
import javax.swing.JPanel
import kotlin.properties.Delegates

/**
 * Modern badge generator tool window using IntelliJ UI DSL.
 */
class BadgeGeneratorToolWindowPanel(private val project: Project) : JPanel(BorderLayout()) {
    private val badgeService = service<BadgeService>()
    private val imageService = service<ImageService>()

    // Selected files
    private var selectedFiles: List<VirtualFile> = emptyList()

    // Property delegates for badge options
    private var badgeText by Delegates.observable("") { _, _, _ -> updatePreview() }
    private var fontSize by Delegates.observable(28) { _, _, _ -> updatePreview() }
    private var badgeShape by Delegates.observable(BadgeOptions.BadgeShape.RECTANGLE) { _, _, _ ->
        updateCustomCornersVisibility()
        updatePreview()
    }
    private var borderRadius by Delegates.observable(4) { _, _, _ -> updatePreview() }
    private var backgroundColor by Delegates.observable(Color.WHITE) { _, _, _ -> updatePreview() }
    private var textColor by Delegates.observable(Color(102, 102, 102)) { _, _, _ -> updatePreview() }
    private var gravity by Delegates.observable(BadgeGravity.SOUTHEAST) { _, _, _ -> updatePreview() }
    private var templateName by Delegates.observable("Default") { _, _, newValue -> applyTemplate(newValue) }

    // Custom corner radius properties
    private var useCustomCorners by Delegates.observable(false) { _, _, _ ->
        updateCustomCornersVisibility()
        updatePreview()
    }
    // Add a flag to prevent recursion
    private var updatingCorners = false

    // Store the target file as a property of the panel
    private var targetImageFile: File? = null

    // Flag to prevent recursive updates
    private var isUpdatingProperties = false

    // Simplified property delegates that don't update each other
    private var topLeftRadius by Delegates.observable(4) { _, _, _ ->
        if (!isUpdatingProperties) {
            if (linkCorners) {
                synchronizeCornerRadii()
            } else {
                updatePreview()
            }
        }
    }

    private var topRightRadius by Delegates.observable(4) { _, _, _ ->
        if (!isUpdatingProperties) updatePreview()
    }

    private var bottomLeftRadius by Delegates.observable(4) { _, _, _ ->
        if (!isUpdatingProperties) updatePreview()
    }

    private var bottomRightRadius by Delegates.observable(4) { _, _, _ ->
        if (!isUpdatingProperties) updatePreview()
    }

    private var linkCorners by Delegates.observable(true) { _, _, newValue ->
        if (newValue) {
            synchronizeCornerRadii()
        } else {
            updatePreview()
        }
    }

    /**
     * Synchronize all corner radii to match the top-left radius
     * without triggering recursive property updates.
     */
    private fun synchronizeCornerRadii() {
        isUpdatingProperties = true
        try {
            // Set all corners to match top-left
            val radius = topLeftRadius
            topRightRadius = radius
            bottomLeftRadius = radius
            bottomRightRadius = radius
        } finally {
            isUpdatingProperties = false
            updatePreview()
        }
    }

    // Preview panel
    private val previewPanel = DynamicBadgePreview(
        targetImageFile = targetImageFile
    ).apply {
        preferredSize = Dimension(250, 250)
        minimumSize = Dimension(200, 200)
    }

    // UI panel
    private lateinit var controlsPanel: DialogPanel

    init {
        // Set up the UI
        border = JBUI.Borders.empty(8)
        setupUI()

        // Initialize with project-specific recommendation
        val recommendation = ProjectIntegrations.analyzeProjectStructure(project)
        badgeText = recommendation.badgeText
        templateName = recommendation.templateName

        // Update the UI with model values
        controlsPanel.reset()
        updatePreview()
        updateCustomCornersVisibility()
    }

    private fun setupUI() {
        // Create splitter for preview and controls
        val splitter = JBSplitter(true, 0.4f)

        // Create preview section
        val previewContainer = panel {
            row {
                label("Preview (Drag to reposition)")
                    .bold()
            }
            row {
                cell(previewPanel)
                    .resizableColumn()
                    .align(Align.FILL)
            }
        }

        // Create controls panel
        controlsPanel = panel {
            // File selection section
            group("Image Files") {
                row {
                    button("Select Image Files...") {
                        selectImageFiles()
                    }
                }
                row {
                    text(getFileSelectionDescription())
                        .resizableColumn()
                        .align(Align.FILL)
                }
                row {
                    button("Apply Badges") {
                        applyBadgesToSelectedFiles()
                    }
                        .enabled(selectedFiles.isNotEmpty())
                }
            }

            // Badge configuration section
            group("Badge Configuration") {
                row("Template:") {
                    comboBox(BadgeOptions.TEMPLATES.keys.toList())
                        .bindItem({ templateName }, {
                            if (it != null && it != templateName) {
                                templateName = it
                            }
                        })
                        .resizableColumn()
                        .align(Align.FILL)
                }

                row("Badge Text:") {
                    textField()
                        .bindText({ badgeText }, {
                            badgeText = it
                        })
                        .resizableColumn()
                        .align(Align.FILL)
                        .focused()
                }

                row("Font Size:") {
                    spinner(8..100, 1)
                        .bindIntValue({ fontSize }, {
                            fontSize = it
                        })
                }

                row("Position:") {
                    comboBox(BadgeGravity.values().toList())
                        .bindItem({ gravity }, {
                            if (it != null) {
                                gravity = it
                            }
                        })
                }

                row("Shape:") {
                    comboBox(BadgeOptions.BadgeShape.values().toList())
                        .bindItem({ badgeShape }, {
                            if (it != null) {
                                badgeShape = it
                            }
                        })
                }

                row("Border Radius:") {
                    spinner(0..30, 1)
                        .bindIntValue({ borderRadius }, {
                            borderRadius = it
                        })
                        .enabledIf(borderRadiusPredicate())
                }

                // Custom corner radius controls
                row {
                    checkBox("Use Custom Corners")
                        .bindSelected({ useCustomCorners }, { useCustomCorners = it })
                        .enabledIf(customCornersPredicate())
                }

                row {
                    checkBox("Link All Corners")
                        .bindSelected({ linkCorners }, { linkCorners = it })
                        .enabledIf(useCustomCornersPredicate())
                }

                // Corner radius spinners in a panel layout
                panel {
                    row("Top Left:") {
                        spinner(0..30, 1)
                            .bindIntValue({ topLeftRadius }, { topLeftRadius = it })
                    }

                    row("Top Right:") {
                        spinner(0..30, 1)
                            .bindIntValue({ topRightRadius }, { topRightRadius = it })
                            .enabledIf(unlinkedCornersPredicate())
                    }

                    row("Bottom Left:") {
                        spinner(0..30, 1)
                            .bindIntValue({ bottomLeftRadius }, { bottomLeftRadius = it })
                            .enabledIf(unlinkedCornersPredicate())
                    }

                    row("Bottom Right:") {
                        spinner(0..30, 1)
                            .bindIntValue({ bottomRightRadius }, { bottomRightRadius = it })
                            .enabledIf(unlinkedCornersPredicate())
                    }
                }.visible(false).apply {
                    name = "cornerRadiiPanel" // For finding it later
                }
            }

            // Colors section
            group("Colors") {
                row("Background:") {
                    val bgColorPanel = ColorPanel().apply {
                        selectedColor = backgroundColor
                    }
                    cell(bgColorPanel)
                        .bind(
                            { it.selectedColor },
                            { component, value -> component.selectedColor = value },
                            propertyOf(backgroundColor, { backgroundColor }, { backgroundColor = it })
                        )
                        .resizableColumn()
                        .align(Align.FILL)
                }

                row("Text Color:") {
                    val textColorPanel = ColorPanel().apply {
                        selectedColor = textColor
                    }
                    cell(textColorPanel)
                        .bind(
                            { it.selectedColor },
                            { component, value -> component.selectedColor = value },
                            propertyOf(textColor, { textColor }, { it?.let { textColor = it } })
                        )
                        .resizableColumn()
                        .align(Align.FILL)
                }
            }
        }

        // Add scrolling to controls
        val scrollPane = JBScrollPane(controlsPanel).apply {
            border = JBUI.Borders.empty()
            horizontalScrollBarPolicy = JBScrollPane.HORIZONTAL_SCROLLBAR_NEVER
            verticalScrollBarPolicy = JBScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
        }

        // Add components to splitter
        splitter.firstComponent = previewContainer
        splitter.secondComponent = scrollPane

        // Add splitter to main panel
        add(splitter, BorderLayout.CENTER)
    }

    // Show/hide custom corner controls based on current state
    private fun updateCustomCornersVisibility() {
        val cornersPanel = findComponentByName(controlsPanel, "cornerRadiiPanel")
        cornersPanel?.isVisible = useCustomCorners && badgeShape == BadgeOptions.BadgeShape.ROUNDED_RECTANGLE

        // Force UI refresh
        controlsPanel.validate()
        controlsPanel.repaint()
    }

    // Helper to find a component by name
    private fun findComponentByName(parent: JPanel, name: String): JPanel? {
        if (parent.name == name) return parent

        for (component in parent.components) {
            if (component is JPanel) {
                if (component.name == name) return component

                val found = findComponentByName(component, name)
                if (found != null) return found
            }
        }

        return null
    }

    /**
     * Get description of selected files.
     */
    private fun getFileSelectionDescription(): String {
        return when {
            selectedFiles.isEmpty() -> "No files selected"
            selectedFiles.size == 1 -> "Selected: ${selectedFiles[0].name}"
            else -> "Selected: ${selectedFiles.size} image files"
        }
    }

    /**
     * Apply template to the UI controls.
     */
    private fun applyTemplate(templateName: String) {
        val template = BadgeOptions.TEMPLATES[templateName] ?: return

        badgeText = template.text
        fontSize = template.fontSize
        backgroundColor = template.backgroundColor
        textColor = template.textColor
        gravity = template.gravity
        badgeShape = template.shape
        borderRadius = template.borderRadius

        // Handle custom corner radii
        if (!template.hasUniformCorners()) {
            useCustomCorners = true
            linkCorners = false
            topLeftRadius = template.getCornerRadius(BadgeOptions.Corner.TOP_LEFT)
            topRightRadius = template.getCornerRadius(BadgeOptions.Corner.TOP_RIGHT)
            bottomLeftRadius = template.getCornerRadius(BadgeOptions.Corner.BOTTOM_LEFT)
            bottomRightRadius = template.getCornerRadius(BadgeOptions.Corner.BOTTOM_RIGHT)
        } else {
            useCustomCorners = false
            linkCorners = true
        }

        // Update UI state
        controlsPanel.reset()
        updateCustomCornersVisibility()
        updatePreview()
    }

    // Predicates for UI components
    private fun borderRadiusPredicate() = object : ComponentPredicate() {
        override fun invoke(): Boolean =
            !useCustomCorners || badgeShape != BadgeOptions.BadgeShape.ROUNDED_RECTANGLE
        override fun addListener(listener: (Boolean) -> Unit) {}
    }

    private fun customCornersPredicate() = object : ComponentPredicate() {
        override fun invoke(): Boolean = badgeShape == BadgeOptions.BadgeShape.ROUNDED_RECTANGLE
        override fun addListener(listener: (Boolean) -> Unit) {}
    }

    private fun useCustomCornersPredicate() = object : ComponentPredicate() {
        override fun invoke(): Boolean =
            useCustomCorners && badgeShape == BadgeOptions.BadgeShape.ROUNDED_RECTANGLE
        override fun addListener(listener: (Boolean) -> Unit) {}
    }

    private fun unlinkedCornersPredicate() = object : ComponentPredicate() {
        override fun invoke(): Boolean = !linkCorners
        override fun addListener(listener: (Boolean) -> Unit) {}
    }

    /**
     * Update the preview based on current settings.
     */
    private fun updatePreview() {
        val options = getBadgeOptions()
        previewPanel.updateBadgeOptions(options)
    }

    /**
     * Get badge options from current settings.
     */
    private fun getBadgeOptions(): BadgeOptions {
        val position = previewPanel.getCurrentPosition()

        return BadgeOptions(
            text = badgeText.ifEmpty { "SAMPLE" },
            backgroundColor = backgroundColor,
            textColor = textColor,
            fontSize = fontSize,
            gravity = gravity,
            shape = badgeShape,
            borderRadius = borderRadius,
            position = position,
            // Include custom corner radii when enabled
            topLeftRadius = if (useCustomCorners && badgeShape == BadgeOptions.BadgeShape.ROUNDED_RECTANGLE)
                topLeftRadius else null,
            topRightRadius = if (useCustomCorners && badgeShape == BadgeOptions.BadgeShape.ROUNDED_RECTANGLE)
                topRightRadius else null,
            bottomLeftRadius = if (useCustomCorners && badgeShape == BadgeOptions.BadgeShape.ROUNDED_RECTANGLE)
                bottomLeftRadius else null,
            bottomRightRadius = if (useCustomCorners && badgeShape == BadgeOptions.BadgeShape.ROUNDED_RECTANGLE)
                bottomRightRadius else null
        )
    }

    /**
     * Open file chooser dialog to select image files.
     */
    private fun selectImageFiles() {
        val fileChooserDescriptor = FileChooserDescriptor(
            true,
            false,
            false,
            false,
            false,
            true
        )
            .withTitle("Select Image Files")
            .withDescription("Choose image files to add badges to")
            .withFileFilter { file ->
                val extension = file.extension?.lowercase()
                extension in listOf("png", "jpg", "jpeg", "gif", "bmp", "webp")
            }

        val files = FileChooser.chooseFiles(fileChooserDescriptor, project, null).toList()

        if (files.isNotEmpty()) {
            selectedFiles = files

            // Update file selection text
            controlsPanel.reset()

            // If we have a single file, store it and update the preview
            if (files.size == 1) {
                targetImageFile = File(files[0].path)

                // Instead of trying to set the file directly on previewPanel,
                // we'll just update the preview with current options
                updatePreview()
            } else {
                // Reset the target file and update the preview
                targetImageFile = null
                updatePreview()
            }
        }
    }

    /**
     * Apply badges to all selected files.
     */
    private fun applyBadgesToSelectedFiles() {
        if (selectedFiles.isEmpty()) {
            Messages.showInfoMessage(
                project,
                "Please select at least one image file first.",
                "No Files Selected"
            )
            return
        }

        val options = getBadgeOptions()

        // Show progress
        ProgressManager.getInstance().run(object : Task.Backgroundable(
            project,
            "Adding Badges to Images",
            true
        ) {
            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = false

                val ioFiles = selectedFiles.map { File(it.path) }
                var processed = 0

                // Process files
                for ((index, file) in ioFiles.withIndex()) {
                    if (indicator.isCanceled) break

                    indicator.fraction = index.toDouble() / ioFiles.size
                    indicator.text = "Processing: ${file.name}"

                    if (badgeService.addBadgeToImage(file, options)) {
                        processed++
                    }
                }

                // Refresh files in IDE
                ApplicationManager.getApplication().invokeLater {
                    selectedFiles.forEach { it.refresh(false, false) }

                    // Show results
                    Messages.showInfoMessage(
                        project,
                        "Successfully added badges to $processed out of ${ioFiles.size} images.",
                        "Process Complete"
                    )
                }
            }
        })
    }

    /**
     * Helper function to create a MutableProperty
     */
    private fun <V> propertyOf(
        initialValue: V,
        getter: () -> V,
        setter: (V) -> Unit
    ): MutableProperty<V> {
        return object : MutableProperty<V> {
            override fun get(): V = getter()
            override fun set(value: V) = setter(value)
        }
    }
}