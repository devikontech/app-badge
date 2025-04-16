package org.devikon.app.badge.ui

import com.intellij.openapi.components.service
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.ui.VerticalFlowLayout
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.CollectionComboBoxModel
import com.intellij.ui.HideableTitledPanel
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import org.devikon.app.badge.model.BadgeGravity
import org.devikon.app.badge.model.BadgeOptions
import org.devikon.app.badge.services.BadgeService
import org.devikon.app.badge.services.ImageService
import java.awt.BorderLayout
import java.awt.Color
import java.io.File
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JSlider
import javax.swing.JSpinner
import javax.swing.SpinnerNumberModel
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

/**
 * Enhanced dialog for configuring badge options with live preview and templates.
 */
class EnhancedAddBadgeDialog(
    private val project: Project,
    private val targetName: String,
    private val targetFile: VirtualFile? = null
) : DialogWrapper(project) {

    private val badgeService = service<BadgeService>()
    private val imageService = service<ImageService>()

    // Basic Controls
    private val textField = JBTextField(20)
    private val fontSizeSpinner = JSpinner(SpinnerNumberModel(28, 8, 100, 1))
    private val templateComboBox = ComboBox<String>(
        CollectionComboBoxModel(BadgeOptions.TEMPLATES.keys.toList())
    )

    // Badge Shape
    private val shapeComboBox = ComboBox<BadgeOptions.BadgeShape>(BadgeOptions.BadgeShape.values())
    private val borderRadiusSpinner = JSpinner(SpinnerNumberModel(4, 0, 50, 1))
    private val borderWidthSpinner = JSpinner(SpinnerNumberModel(0, 0, 10, 1))

    // Colors
    private val bgColorButton = ColorPickerButton(Color.WHITE)
    private val textColorButton = ColorPickerButton(Color(102, 102, 102))
    private val shadowColorButton = ColorPickerButton(Color(0, 0, 0, 153))
    private val borderColorButton = ColorPickerButton(Color.BLACK)

    // Gradient
    private val useGradientCheckbox = JBCheckBox("Use Gradient")
    private val gradientEndColorButton = ColorPickerButton(Color(52, 152, 219))

    // Position
    private val gravityComboBox = ComboBox(BadgeGravity.values())
    private val fontChooser = TextFieldWithBrowseButton()
    private val opacitySlider = JSlider(0, 100, 100)
    private val shadowSizeSpinner = JSpinner(SpinnerNumberModel(4, 0, 20, 1))

    // Preview panel
    private val previewPanel = DynamicBadgePreview(
        targetImageFile = targetFile?.path?.let { File(it) }
    )

    // Current options state
    private var currentOptions = BadgeOptions("DEV")

    init {
        title = "Add Badge to $targetName"
        init()

        // Set up controls and layout
        setupControls()

        // Initial template selection
        val recommendedTemplate = badgeService.analyzeProjectForBadgeTemplate(project)
        templateComboBox.selectedItem = recommendedTemplate

        // Load initial text based on project analysis
        val suggestedText = BadgeOptions.detectEnvironment(project.basePath ?: "")
        textField.text = suggestedText

        // Apply initial template
        applyTemplate(recommendedTemplate)

        // Update the preview with initial options
        updatePreview()
    }

    /**
     * Set up all UI controls and their event listeners.
     */
    private fun setupControls() {
        // Configure the font chooser
        val descriptor = FileChooserDescriptor(true, false, false, false, false, false)
            .withFileFilter { it.extension?.lowercase() == "ttf" }
            .withTitle("Select Font File")

        fontChooser.addBrowseFolderListener(
            "Select Font",
            "Choose a TTF font file",
            project,
            descriptor
        )

        // Set up listeners for template selection
        templateComboBox.addActionListener {
            val selectedTemplate = templateComboBox.selectedItem as String
            applyTemplate(selectedTemplate)
            updatePreview()
        }

        // Set up listeners for preview updates
        val updateListener = { updatePreview() }

        textField.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent) = updatePreview()
            override fun removeUpdate(e: DocumentEvent) = updatePreview()
            override fun changedUpdate(e: DocumentEvent) = updatePreview()
        })

        fontSizeSpinner.addChangeListener { updatePreview() }
        shapeComboBox.addActionListener { updatePreview() }
        borderRadiusSpinner.addChangeListener { updatePreview() }
        borderWidthSpinner.addChangeListener { updatePreview() }

        bgColorButton.addActionListener { updatePreview() }
        textColorButton.addActionListener { updatePreview() }
        shadowColorButton.addActionListener { updatePreview() }
        borderColorButton.addActionListener { updatePreview() }

        useGradientCheckbox.addActionListener {
            gradientEndColorButton.isEnabled = useGradientCheckbox.isSelected
            updatePreview()
        }
        gradientEndColorButton.addActionListener { updatePreview() }

        gravityComboBox.addActionListener { updatePreview() }
        fontChooser.textField.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent) = updatePreview()
            override fun removeUpdate(e: DocumentEvent) = updatePreview()
            override fun changedUpdate(e: DocumentEvent) = updatePreview()
        })

        opacitySlider.addChangeListener { updatePreview() }
        shadowSizeSpinner.addChangeListener { updatePreview() }

        // Initial state
        gradientEndColorButton.isEnabled = useGradientCheckbox.isSelected

        // Initial selection
        gravityComboBox.selectedItem = BadgeGravity.SOUTHEAST
    }

    /**
     * Apply a template to the UI controls.
     */
    private fun applyTemplate(templateName: String) {
        val template = BadgeOptions.TEMPLATES[templateName] ?: return

        // Apply template values to UI controls
        textField.text = template.text
        fontSizeSpinner.value = template.fontSize
        bgColorButton.setSelectedColor(template.backgroundColor)
        textColorButton.setSelectedColor(template.textColor)
        shadowColorButton.setSelectedColor(template.shadowColor)
        shadowSizeSpinner.value = template.shadowSize
        gravityComboBox.selectedItem = template.gravity
        shapeComboBox.selectedItem = template.shape
        borderRadiusSpinner.value = template.borderRadius
        borderWidthSpinner.value = template.borderWidth
        borderColorButton.setSelectedColor(template.borderColor ?: Color.BLACK)
        useGradientCheckbox.isSelected = template.useGradient
        gradientEndColorButton.isEnabled = template.useGradient

        if (template.useGradient && template.gradientEndColor != null) {
            gradientEndColorButton.setSelectedColor(template.gradientEndColor)
        }

        opacitySlider.value = (template.opacity * 100).toInt()
    }

    /**
     * Update the preview based on current UI settings.
     */
    private fun updatePreview() {
        val options = getBadgeOptions()
        if (options != currentOptions) {
            currentOptions = options
            previewPanel.updateBadgeOptions(options)
        }
    }

    override fun createCenterPanel(): JComponent {
        // Create a splitter for form and preview
        val splitter = OnePixelSplitter(false, 0.6f)

        // Set up the form with sections
        val formScrollPane = JBScrollPane(createFormPanel())
        formScrollPane.border = JBUI.Borders.empty()

        splitter.firstComponent = formScrollPane

        // Set up the preview panel
        val previewContainer = JPanel(BorderLayout())
        previewContainer.border = JBUI.Borders.empty(10)

        val previewLabel = JBLabel("Preview: (Drag to position)")
        previewLabel.border = JBUI.Borders.emptyBottom(5)

        previewContainer.add(previewLabel, BorderLayout.NORTH)
        previewContainer.add(previewPanel, BorderLayout.CENTER)

        splitter.secondComponent = previewContainer

        return splitter
    }

    /**
     * Create the form panel with all controls, organized into collapsible sections.
     */
    private fun createFormPanel(): JComponent {
        // Basic settings section
        val basicSection = FormBuilder.createFormBuilder()
            .addLabeledComponent("Template:", templateComboBox)
            .addLabeledComponent("Badge Text:", textField)
            .addLabeledComponent("Font Size:", fontSizeSpinner)
            .addLabeledComponent("Position:", gravityComboBox)
            .panel

        val basicPanel = HideableTitledPanel("Basic Settings", true, basicSection, true)

        // Appearance section
        val appearanceSection = FormBuilder.createFormBuilder()
            .addLabeledComponent("Shape:", shapeComboBox)
            .addLabeledComponent("Background:", createColorRow(bgColorButton, useGradientCheckbox))
            .addLabeledComponent("Gradient End:", gradientEndColorButton)
            .addLabeledComponent("Text Color:", textColorButton)
            .addLabeledComponent("Opacity:", opacitySlider)
            .panel

        val appearancePanel = HideableTitledPanel("Appearance", appearanceSection, true)

        // Border section
        val borderSection = FormBuilder.createFormBuilder()
            .addLabeledComponent("Border Radius:", borderRadiusSpinner)
            .addLabeledComponent("Border Width:", borderWidthSpinner)
            .addLabeledComponent("Border Color:", borderColorButton)
            .panel

        val borderPanel = HideableTitledPanel("Border", borderSection, false)

        // Advanced section
        val advancedSection = FormBuilder.createFormBuilder()
            .addLabeledComponent("Shadow Size:", shadowSizeSpinner)
            .addLabeledComponent("Shadow Color:", shadowColorButton)
            .addLabeledComponent("Custom Font:", fontChooser)
            .panel

        val advancedPanel = HideableTitledPanel("Advanced", advancedSection, false)

        // Combine all sections
        val mainPanel = JPanel(VerticalFlowLayout(0, 0))
        mainPanel.add(basicPanel)
        mainPanel.add(appearancePanel)
        mainPanel.add(borderPanel)
        mainPanel.add(advancedPanel)

        return mainPanel
    }

    /**
     * Create a row with color button and checkbox.
     */
    private fun createColorRow(colorButton: ColorPickerButton, checkbox: JCheckBox): JComponent {
        val panel = JPanel(BorderLayout(5, 0))
        panel.add(colorButton, BorderLayout.WEST)
        panel.add(checkbox, BorderLayout.CENTER)
        return panel
    }

    /**
     * Get the configured badge options from the dialog.
     */
    fun getBadgeOptions(): BadgeOptions {
        val fontFilePath = fontChooser.text
        val fontFile = if (fontFilePath.isNotBlank()) File(fontFilePath) else null

        // Use position from preview if available
        val position = previewPanel.getCurrentPosition()

        return BadgeOptions(
            text = textField.text,
            backgroundColor = bgColorButton.selectedColor,
            textColor = textColorButton.selectedColor,
            shadowColor = shadowColorButton.selectedColor,
            fontSize = fontSizeSpinner.value as Int,
            gravity = gravityComboBox.selectedItem as BadgeGravity,
            fontFile = fontFile,
            position = position,
            shape = shapeComboBox.selectedItem as BadgeOptions.BadgeShape,
            borderRadius = borderRadiusSpinner.value as Int,
            borderWidth = borderWidthSpinner.value as Int,
            borderColor = if (borderWidthSpinner.value as Int > 0) borderColorButton.selectedColor else null,
            useGradient = useGradientCheckbox.isSelected,
            gradientEndColor = if (useGradientCheckbox.isSelected) gradientEndColorButton.selectedColor else null,
            opacity = opacitySlider.value / 100f,
            shadowSize = shadowSizeSpinner.value as Int
        )
    }
}