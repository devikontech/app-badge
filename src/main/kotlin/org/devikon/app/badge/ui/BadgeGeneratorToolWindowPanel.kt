package org.devikon.app.badge.ui

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.CollectionComboBoxModel
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import org.devikon.app.badge.integrations.ProjectIntegrations
import org.devikon.app.badge.listeners.BadgePreviewComponent
import org.devikon.app.badge.model.BadgeGravity
import org.devikon.app.badge.model.BadgeOptions
import org.devikon.app.badge.services.BadgeService
import org.devikon.app.badge.services.ImageService
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.io.File
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JPanel
import javax.swing.JSpinner
import javax.swing.SpinnerNumberModel
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

/**
 * Main panel for the Badge Generator tool window.
 */
class BadgeGeneratorToolWindowPanel(private val project: Project) : JPanel(BorderLayout()) {

    private val badgeService = service<BadgeService>()
    private val imageService = service<ImageService>()

    // Selected files
    private var selectedFiles: List<VirtualFile> = emptyList()

    // UI Controls
    private val fileSelectionLabel = JBLabel("No files selected")
    private val selectFilesButton = JButton("Select Image Files...")
    private val templateComboBox = JComboBox<String>()
    private val textField = JBTextField(10)
    private val fontSizeSpinner = JSpinner(SpinnerNumberModel(28, 8, 100, 1))
    private val gravityComboBox = JComboBox(BadgeGravity.values())
    private val bgColorButton = ColorPickerButton(Color.WHITE)
    private val textColorButton = ColorPickerButton(Color(102, 102, 102))
    private val applyButton = JButton("Apply Badges")

    // Preview panel
    private val previewPanel = BadgePreviewComponent()

    init {
        setupUI()
        setupListeners()

        // Try to get a suggested badge template and text based on project
        val recommendation = ProjectIntegrations.analyzeProjectStructure(project)
        textField.text = recommendation.badgeText

        // Find template in the combo box
        for (i in 0 until templateComboBox.itemCount) {
            if (templateComboBox.getItemAt(i) == recommendation.templateName) {
                templateComboBox.selectedIndex = i
                break
            }
        }

        // Update preview
        updatePreview()
    }

    private fun setupUI() {
        // Set border and minimum size
        border = JBUI.Borders.empty(10)
        minimumSize = Dimension(300, 500)

        // Set up the controls panel
        val controlsPanel = JPanel(GridBagLayout())
        val gbc = GridBagConstraints().apply {
            fill = GridBagConstraints.HORIZONTAL
            insets = Insets(5, 5, 5, 5)
            gridx = 0
            gridy = 0
            weightx = 0.0
        }

        // File selection section
        controlsPanel.add(JBLabel("Image Files:"), gbc.apply { gridy++ })

        val fileSelectionPanel = JPanel(BorderLayout())
        fileSelectionPanel.add(fileSelectionLabel, BorderLayout.CENTER)
        fileSelectionPanel.add(selectFilesButton, BorderLayout.EAST)
        controlsPanel.add(fileSelectionPanel, gbc.apply {
            gridy++
            gridwidth = 2
            weightx = 1.0
        })

        // Reset gridwidth
        gbc.apply {
            gridwidth = 1
            weightx = 0.0
        }

        // Badge configuration section
        controlsPanel.add(JBLabel("Template:"), gbc.apply { gridy++ })

        // Populate templates combo box
        templateComboBox.model = CollectionComboBoxModel(BadgeOptions.TEMPLATES.keys.toList())
        controlsPanel.add(templateComboBox, gbc.apply {
            gridx = 1
            weightx = 1.0
        })

        controlsPanel.add(JBLabel("Badge Text:"), gbc.apply {
            gridx = 0
            gridy++
            weightx = 0.0
        })
        controlsPanel.add(textField, gbc.apply {
            gridx = 1
            weightx = 1.0
        })

        controlsPanel.add(JBLabel("Font Size:"), gbc.apply {
            gridx = 0
            gridy++
            weightx = 0.0
        })
        controlsPanel.add(fontSizeSpinner, gbc.apply {
            gridx = 1
            weightx = 1.0
        })

        controlsPanel.add(JBLabel("Position:"), gbc.apply {
            gridx = 0
            gridy++
            weightx = 0.0
        })
        controlsPanel.add(gravityComboBox, gbc.apply {
            gridx = 1
            weightx = 1.0
        })

        controlsPanel.add(JBLabel("Background:"), gbc.apply {
            gridx = 0
            gridy++
            weightx = 0.0
        })
        controlsPanel.add(bgColorButton, gbc.apply {
            gridx = 1
            weightx = 1.0
        })

        controlsPanel.add(JBLabel("Text Color:"), gbc.apply {
            gridx = 0
            gridy++
            weightx = 0.0
        })
        controlsPanel.add(textColorButton, gbc.apply {
            gridx = 1
            weightx = 1.0
        })

        // Add the controls to a scroll pane
        val scrollPane = JBScrollPane(controlsPanel)
        scrollPane.border = null

        // Set up preview section
        val previewLabel = JBLabel("Preview:")
        previewPanel.preferredSize = Dimension(250, 150)
        previewPanel.border = BorderFactory.createLoweredBevelBorder()

        val previewContainer = JPanel(BorderLayout())
        previewContainer.add(previewLabel, BorderLayout.NORTH)
        previewContainer.add(previewPanel, BorderLayout.CENTER)
        previewContainer.border = JBUI.Borders.empty(10, 0, 10, 0)

        // Set up action button
        val buttonPanel = JPanel(FlowLayout(FlowLayout.RIGHT))
        buttonPanel.add(applyButton)

        // Assemble the main layout
        add(scrollPane, BorderLayout.CENTER)
        add(previewContainer, BorderLayout.NORTH)
        add(buttonPanel, BorderLayout.SOUTH)
    }

    private fun setupListeners() {
        // File selection button
        selectFilesButton.addActionListener {
            selectImageFiles()
        }

        // Template selection
        templateComboBox.addActionListener {
            val selectedTemplate = templateComboBox.selectedItem as String? ?: return@addActionListener
            applyTemplate(selectedTemplate)
            updatePreview()
        }

        // Text field changes
        textField.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent) = updatePreview()
            override fun removeUpdate(e: DocumentEvent) = updatePreview()
            override fun changedUpdate(e: DocumentEvent) = updatePreview()
        })

        // Font size spinner
        fontSizeSpinner.addChangeListener { updatePreview() }

        // Gravity combo box
        gravityComboBox.addActionListener { updatePreview() }

        // Color buttons
        bgColorButton.addActionListener { updatePreview() }
        textColorButton.addActionListener { updatePreview() }

        // Apply button
        applyButton.addActionListener {
            applyBadgesToSelectedFiles()
        }
    }

    /**
     * Apply a template to the UI controls.
     */
    private fun applyTemplate(templateName: String) {
        val template = BadgeOptions.TEMPLATES[templateName] ?: return

        // Apply values to UI controls
        textField.text = template.text
        fontSizeSpinner.value = template.fontSize
        gravityComboBox.selectedItem = template.gravity
        bgColorButton.setSelectedColor(template.backgroundColor)
        textColorButton.setSelectedColor(template.textColor)
    }

    /**
     * Update the preview based on current settings.
     */
    private fun updatePreview() {
        val options = getBadgeOptions()
        previewPanel.updateOptions(options)
    }

    /**
     * Get badge options from the UI controls.
     */
    private fun getBadgeOptions(): BadgeOptions {
        return BadgeOptions(
            text = textField.text.ifEmpty { "SAMPLE" },
            backgroundColor = bgColorButton.selectedColor,
            textColor = textColorButton.selectedColor,
            fontSize = fontSizeSpinner.value as Int,
            gravity = gravityComboBox.selectedItem as BadgeGravity,
            position = previewPanel.getCurrentPosition()
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

        val files = FileChooser.chooseFiles(fileChooserDescriptor, project, null)
        if (files.isNotEmpty()) {
            selectedFiles = files.asList()

            // Update the label
            fileSelectionLabel.text = if (files.size == 1) {
                files[0].name
            } else {
                "${files.size} image files selected"
            }

            // If we have a single file, update the preview
            if (files.size == 1) {
                val file = File(files[0].path)
                previewPanel.setTargetImageFile(file)
            } else {
                // Create a generic preview
                previewPanel.setTargetImageFile(null)
            }

            // Enable apply button
            applyButton.isEnabled = true
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
        val progressManager = ProgressManager.getInstance()
        progressManager.run(object : Task.Backgroundable(
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
}