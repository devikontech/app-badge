package org.devikon.app.badge.ui

import com.intellij.openapi.components.service
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.ColorPanel
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.components.JBLabel
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.MutableProperty
import com.intellij.ui.dsl.builder.bindIntValue
import com.intellij.ui.dsl.builder.bindItem
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.bindValue
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
import java.io.File
import javax.swing.JComponent
import javax.swing.JPanel
import kotlin.properties.Delegates

/**
 * Enhanced dialog for configuring badge options, using IntelliJ UI DSL.
 */
class EnhancedAddBadgeDialog(
    private val project: Project,
    private val targetName: String,
    private val targetFile: VirtualFile? = null
) : DialogWrapper(project) {

    private val badgeService = service<BadgeService>()
    private val imageService = service<ImageService>()

    // Property delegates for badge options
    private var badgeText by Delegates.observable("") { _, _, _ -> updatePreview() }
    private var fontSize by Delegates.observable(28) { _, _, _ -> updatePreview() }
    private var badgeShape by Delegates.observable(BadgeOptions.BadgeShape.RECTANGLE) { _, _, _ -> updatePreview() }
    private var borderRadius by Delegates.observable(4) { _, _, _ -> updatePreview() }
    private var borderWidth by Delegates.observable(0) { _, _, _ -> updatePreview() }
    private var backgroundColor by Delegates.observable(Color.WHITE) { _, _, _ -> updatePreview() }
    private var textColor by Delegates.observable(Color(102, 102, 102)) { _, _, _ -> updatePreview() }
    private var shadowColor by Delegates.observable(Color(0, 0, 0, 153)) { _, _, _ -> updatePreview() }
    private var borderColor by Delegates.observable(Color.BLACK) { _, _, _ -> updatePreview() }
    private var useGradient by Delegates.observable(false) { _, _, _ -> updatePreview() }
    private var gradientEndColor by Delegates.observable(Color(52, 152, 219)) { _, _, _ -> updatePreview() }
    private var gravity by Delegates.observable(BadgeGravity.SOUTHEAST) { _, _, _ -> updatePreview() }
    private var fontFilePath by Delegates.observable("") { _, _, _ -> updatePreview() }
    private var opacity by Delegates.observable(1.0f) { _, _, _ -> updatePreview() }
    private var shadowSize by Delegates.observable(4) { _, _, _ -> updatePreview() }
    private var templateName by Delegates.observable("Default") { _, _, newValue -> applyTemplate(newValue) }

    // Custom corner radius properties
    private var useCustomCorners by Delegates.observable(false) { _, _, _ -> updatePreview() }
    private var topLeftRadius by Delegates.observable(4) { _, _, _ -> updatePreview() }
    private var topRightRadius by Delegates.observable(4) { _, _, _ -> updatePreview() }
    private var bottomLeftRadius by Delegates.observable(4) { _, _, _ -> updatePreview() }
    private var bottomRightRadius by Delegates.observable(4) { _, _, _ -> updatePreview() }
    private var linkCorners by Delegates.observable(true) { _, _, newValue ->
        if (newValue) {
            // When corners are linked, set all corners to match top-left
            topRightRadius = topLeftRadius
            bottomLeftRadius = topLeftRadius
            bottomRightRadius = topLeftRadius
        }
        updatePreview()
    }

    // Preview component
    private val previewPanel = DynamicBadgePreview(
        targetImageFile = targetFile?.path?.let { File(it) }
    )

    init {
        title = "Add Badge to $targetName"

        // Initialize with project-specific recommendation
        val recommendation = ProjectIntegrations.analyzeProjectStructure(project)
        badgeText = recommendation.badgeText
        templateName = recommendation.templateName

        init()
    }

    /**
     * Apply a template to the UI controls.
     */
    private fun applyTemplate(templateName: String) {
        val template = BadgeOptions.TEMPLATES[templateName] ?: return

        // Update all properties without triggering preview update for each one
        badgeText = template.text
        fontSize = template.fontSize
        backgroundColor = template.backgroundColor
        textColor = template.textColor
        shadowColor = template.shadowColor
        shadowSize = template.shadowSize
        gravity = template.gravity
        badgeShape = template.shape
        borderRadius = template.borderRadius
        borderWidth = template.borderWidth
        borderColor = template.borderColor ?: Color.BLACK
        useGradient = template.useGradient
        opacity = template.opacity

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

        // Update the preview
        updatePreview()
    }

    /**
     * Update the preview based on current settings.
     */
    private fun updatePreview() {
        val options = getBadgeOptions()
        previewPanel.updateBadgeOptions(options)
    }

    override fun createCenterPanel(): JComponent {
        // Create a splitter for form and preview
        val splitter = OnePixelSplitter(false, 0.55f)

        // Set up the form with tabs using IntelliJ UI DSL
        splitter.firstComponent = createFormPanel()

        // Set up the preview panel
        val previewContainer = JPanel(BorderLayout())
        previewContainer.border = JBUI.Borders.empty(10)

        val previewLabel = JBLabel("Preview (Drag to position)")
        previewLabel.border = JBUI.Borders.emptyBottom(5)

        previewContainer.add(previewLabel, BorderLayout.NORTH)
        previewContainer.add(previewPanel, BorderLayout.CENTER)

        splitter.secondComponent = previewContainer

        return splitter
    }

    /**
     * Create the form panel with tabs and sections using IntelliJ UI DSL.
     */
    private fun createFormPanel(): DialogPanel {
        return panel {
            // Template/preset selection (always visible)
            row("Template:") {
                comboBox(BadgeOptions.TEMPLATES.keys.toList())
                    .bindItem({ templateName }, { templateName = it ?: "Default" })
                    .resizableColumn()
                    .align(Align.FILL)
            }

            // Main settings
            group("Badge Settings") {
                row("Text:") {
                    textField()
                        .bindText({ badgeText }, { badgeText = it })
                        .resizableColumn()
                        .align(Align.FILL)
                        .focused()
                }

                row("Font Size:") {
                    spinner(8..100, 1)
                        .bindIntValue({ fontSize }, { fontSize = it })
                }

                row("Position:") {
                    comboBox(BadgeGravity.values().toList())
                        .bindItem({ gravity }, { gravity = it ?: BadgeGravity.SOUTHEAST })
                }

                row("Shape:") {
                    comboBox(BadgeOptions.BadgeShape.values().toList())
                        .bindItem({ badgeShape }, { badgeShape = it ?: BadgeOptions.BadgeShape.RECTANGLE })
                }

                row("Opacity:") {
                    slider(0, 100, 5, 100)
                        .bindValue(
                            { (opacity * 100).toInt() },
                            { opacity = it.toFloat() / 100 }
                        )
                }
            }

            // Appearance settings in a collapsible section
            collapsibleGroup("Colors & Appearance") {
                row("Background:") {
                    val bgColorPanel = ColorPanel()
                    bgColorPanel.selectedColor = backgroundColor
                    cell(bgColorPanel)
                        .bind(
                            { it.selectedColor },
                            { component, value -> component.selectedColor = value },
                            propertyOf(backgroundColor, { backgroundColor }, { backgroundColor = it })
                        )
                        .resizableColumn()
                        .align(Align.FILL)
                }

                row {
                    checkBox("Use Gradient")
                        .bindSelected({ useGradient }, { useGradient = it })
                }

                row("Gradient End:") {
                    val gradientColorPanel = ColorPanel()
                    gradientColorPanel.selectedColor = gradientEndColor
                    cell(gradientColorPanel)
                        .bind(
                            componentGet = { it.selectedColor },
                            componentSet = { component, value -> component.selectedColor = value },
                            prop = propertyOf(
                                initialValue = gradientEndColor,
                                getter = { gradientEndColor },
                                setter = { it?.let { gradientEndColor = it } })
                        )
                        .enabledIf(useGradientPredicate())
                }

                row("Text Color:") {
                    val textColorPanel = ColorPanel()
                    textColorPanel.selectedColor = textColor
                    cell(textColorPanel)
                        .bind(
                            componentGet = { it.selectedColor },
                            componentSet = { component, value -> component.selectedColor = value },
                            prop = propertyOf(
                                initialValue = textColor,
                                getter = { textColor },
                                setter = { it?.let { textColor = it } }
                            )
                        )
                }
            }

            // Border and corner settings
            group("Border & Corners") {
                row("Border Width:") {
                    spinner(0..10, 1)
                        .bindIntValue({ borderWidth }, { borderWidth = it })
                }

                row("Border Color:") {
                    val borderColorPanel = ColorPanel()
                    borderColorPanel.selectedColor = borderColor
                    cell(borderColorPanel)
                        .bind(
                            { it.selectedColor },
                            { component, value -> component.selectedColor = value },
                            propertyOf(borderColor, { borderColor }, { borderColor = it })
                        )
                        .enabledIf(borderWidthPredicate())
                }

                // Standard border radius (visible when not using custom corners)
                row("Border Radius:") {
                    spinner(0..50, 1)
                        .bindIntValue({ borderRadius }, { borderRadius = it })
                        .enabledIf(borderRadiusPredicate())
                }

                // Custom corner radius section
                row {
                    checkBox("Use Custom Corner Radii")
                        .bindSelected({ useCustomCorners }, { useCustomCorners = it })
                        .enabledIf(customCornersPredicate())
                }

                // Link corners checkbox
                row {
                    checkBox("Link All Corners")
                        .bindSelected({ linkCorners }, { linkCorners = it })
                        .enabledIf(linkCornersPredicate())
                        .comment("When enabled, all corners will have the same radius")
                }

                // Custom corner radii spinners in a panel layout
                panel {
                    row("Top Left:") {
                        spinner(0..50, 1)
                            .bindIntValue({ topLeftRadius }, {
                                topLeftRadius = it
                                if (linkCorners) {
                                    topRightRadius = it
                                    bottomLeftRadius = it
                                    bottomRightRadius = it
                                }
                            })
                            .enabledIf(customRadiiPredicate())
                    }

                    row("Top Right:") {
                        spinner(0..50, 1)
                            .bindIntValue({ topRightRadius }, { topRightRadius = it })
                            .enabledIf(unlinkedRadiiPredicate())
                    }

                    row("Bottom Left:") {
                        spinner(0..50, 1)
                            .bindIntValue({ bottomLeftRadius }, { bottomLeftRadius = it })
                            .enabledIf(unlinkedRadiiPredicate())
                    }

                    row("Bottom Right:") {
                        spinner(0..50, 1)
                            .bindIntValue({ bottomRightRadius }, { bottomRightRadius = it })
                            .enabledIf(unlinkedRadiiPredicate())
                    }
                }.enabledIf(customRadiiPredicate())
            }

            // Advanced settings in a collapsible section
            collapsibleGroup("Advanced", true) {
                row("Shadow Size:") {
                    spinner(0..20, 1)
                        .bindIntValue({ shadowSize }, { shadowSize = it })
                }

                row("Shadow Color:") {
                    val shadowColorPanel = ColorPanel()
                    shadowColorPanel.selectedColor = shadowColor
                    cell(shadowColorPanel)
                        .bind(
                            componentGet = { it.selectedColor },
                            componentSet = { component, value -> component.selectedColor = value },
                            prop = propertyOf(
                                initialValue = shadowColor,
                                getter = { shadowColor },
                                setter = { it?.let { shadowColor = it } })
                        )
                        .enabledIf(shadowSizePredicate())
                }

                row("Custom Font:") {
                    textFieldWithBrowseButton(
                        browseDialogTitle = "Select TTF Font File",
                        fileChooserDescriptor = FileChooserDescriptorFactory.createSingleFileDescriptor("ttf")
                            .withFileFilter { it.extension?.lowercase() == "ttf" }
                    )
                        .bindText({ fontFilePath }, { fontFilePath = it })
                        .resizableColumn()
                        .align(Align.FILL)
                }
            }
        }
    }

    // Predicates for UI element visibility and enabling conditions
    private fun useGradientPredicate() = object : ComponentPredicate() {
        override fun invoke(): Boolean = useGradient
        override fun addListener(listener: (Boolean) -> Unit) {}
    }

    private fun borderWidthPredicate() = object : ComponentPredicate() {
        override fun invoke(): Boolean = borderWidth > 0
        override fun addListener(listener: (Boolean) -> Unit) {}
    }

    private fun customCornersPredicate() = object : ComponentPredicate() {
        override fun invoke(): Boolean = badgeShape == BadgeOptions.BadgeShape.ROUNDED_RECTANGLE
        override fun addListener(listener: (Boolean) -> Unit) {}
    }

    private fun linkCornersPredicate() = object : ComponentPredicate() {
        override fun invoke(): Boolean = useCustomCorners && badgeShape == BadgeOptions.BadgeShape.ROUNDED_RECTANGLE
        override fun addListener(listener: (Boolean) -> Unit) {}
    }

    private fun borderRadiusPredicate() = object : ComponentPredicate() {
        override fun invoke(): Boolean =
            badgeShape == BadgeOptions.BadgeShape.ROUNDED_RECTANGLE && !useCustomCorners ||
                    badgeShape == BadgeOptions.BadgeShape.PILL
        override fun addListener(listener: (Boolean) -> Unit) {}
    }

    private fun customRadiiPredicate() = object : ComponentPredicate() {
        override fun invoke(): Boolean = useCustomCorners && badgeShape == BadgeOptions.BadgeShape.ROUNDED_RECTANGLE
        override fun addListener(listener: (Boolean) -> Unit) {}
    }

    private fun unlinkedRadiiPredicate() = object : ComponentPredicate() {
        override fun invoke(): Boolean =
            useCustomCorners &&
                    badgeShape == BadgeOptions.BadgeShape.ROUNDED_RECTANGLE &&
                    !linkCorners
        override fun addListener(listener: (Boolean) -> Unit) {}
    }

    private fun shadowSizePredicate() = object : ComponentPredicate() {
        override fun invoke(): Boolean = shadowSize > 0
        override fun addListener(listener: (Boolean) -> Unit) {}
    }

    /**
     * Validate user input.
     */
    override fun doValidate(): ValidationInfo? {
        if (badgeText.isBlank()) {
            return ValidationInfo("Badge text cannot be empty")
        }

        if (fontFilePath.isNotBlank()) {
            val fontFile = File(fontFilePath)
            if (!fontFile.exists() || !fontFile.isFile) {
                return ValidationInfo("Font file does not exist")
            }
        }

        return null
    }

    /**
     * Get the configured badge options from the dialog.
     */
    fun getBadgeOptions(): BadgeOptions {
        val fontFile = if (fontFilePath.isNotBlank()) File(fontFilePath) else null
        val position = previewPanel.getCurrentPosition()

        return BadgeOptions(
            text = badgeText.ifBlank { "SAMPLE" },
            backgroundColor = backgroundColor,
            textColor = textColor,
            shadowColor = shadowColor,
            fontSize = fontSize,
            gravity = gravity,
            fontFile = fontFile,
            position = position,
            shape = badgeShape,
            borderRadius = borderRadius,
            // Include individual corner radii only when using custom corners
            topLeftRadius = if (useCustomCorners && badgeShape == BadgeOptions.BadgeShape.ROUNDED_RECTANGLE)
                topLeftRadius else null,
            topRightRadius = if (useCustomCorners && badgeShape == BadgeOptions.BadgeShape.ROUNDED_RECTANGLE)
                topRightRadius else null,
            bottomLeftRadius = if (useCustomCorners && badgeShape == BadgeOptions.BadgeShape.ROUNDED_RECTANGLE)
                bottomLeftRadius else null,
            bottomRightRadius = if (useCustomCorners && badgeShape == BadgeOptions.BadgeShape.ROUNDED_RECTANGLE)
                bottomRightRadius else null,
            borderWidth = borderWidth,
            borderColor = if (borderWidth > 0) borderColor else null,
            useGradient = useGradient,
            gradientEndColor = if (useGradient) gradientEndColor else null,
            opacity = opacity,
            shadowSize = shadowSize
        )
    }

    /**
     * Helper function to create a MutableProperty for binding
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