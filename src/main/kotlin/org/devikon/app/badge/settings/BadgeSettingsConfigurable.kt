package org.devikon.app.badge.settings

import com.intellij.openapi.components.service
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.CollectionComboBoxModel
import com.intellij.ui.JBColor
import com.intellij.ui.JBIntSpinner
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTabbedPane
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.table.JBTable
import org.devikon.app.badge.model.BadgeGravity
import org.devikon.app.badge.model.BadgeOptions
import org.devikon.app.badge.services.BadgeService
import org.devikon.app.badge.services.ImageService
import org.devikon.app.badge.ui.ColorPickerButton
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.ListSelectionModel
import javax.swing.table.AbstractTableModel

/**
 * Settings configurable for the Badge Generator plugin.
 */
class BadgeSettingsConfigurable : Configurable {
    private val settings = service<BadgeSettings>()

    // UI Components
    private var mainPanel: JPanel? = null
    private var defaultTemplateCombo: ComboBox<String>? = null
    private var defaultGravityCombo: ComboBox<BadgeGravity>? = null
    private var defaultFontSizeSpinner: JBIntSpinner? = null
    private var defaultBackgroundColorButton: ColorPickerButton? = null
    private var defaultTextColorButton: ColorPickerButton? = null
    private var defaultShadowColorButton: ColorPickerButton? = null
    private var defaultBorderRadiusSpinner: JBIntSpinner? = null
    private var integrateWithAndroidCheckbox: JBCheckBox? = null
    private var integrateWithGitCheckbox: JBCheckBox? = null
    private var enableCachingCheckbox: JBCheckBox? = null
    private var maxCacheSizeSpinner: JBIntSpinner? = null
    private var customTemplatesTable: JBTable? = null
    private var templatesTableModel: CustomTemplatesTableModel? = null
    private var previewComponent: BadgePreviewComponent? = null

    // Tracking for modified state
    private var modified = false

    override fun getDisplayName(): String = "Badge Generator"

    override fun createComponent(): JComponent {
        // Create table model for custom templates
        templatesTableModel = CustomTemplatesTableModel(settings.customTemplates)

        // Create main tabbed panel
        val tabbedPane = JBTabbedPane()

        // General settings tab
        val generalPanel = createGeneralSettingsPanel()
        tabbedPane.addTab("General", generalPanel)

        // Advanced settings tab
        val advancedPanel = createAdvancedSettingsPanel()
        tabbedPane.addTab("Advanced", advancedPanel)

        // Custom templates tab
        val templatesPanel = createTemplatesPanel()
        tabbedPane.addTab("Templates", templatesPanel)

        // Create preview component
        previewComponent = BadgePreviewComponent()
        previewComponent!!.preferredSize = Dimension(300, 150)
        previewComponent!!.updateOptions(getBadgePreviewOptions())

        // Main panel with tabs and preview
        mainPanel = JPanel(BorderLayout())
        mainPanel!!.add(tabbedPane, BorderLayout.CENTER)
        mainPanel!!.add(createPreviewPanel(), BorderLayout.SOUTH)

        // Add change listeners
        setupChangeListeners()

        return mainPanel!!
    }

    /**
     * Create the general settings panel.
     */
    private fun createGeneralSettingsPanel(): DialogPanel {
        return panel {
            group("Default Badge Settings") {
                row("Default Template:") {
                    defaultTemplateCombo = comboBox(
                        CollectionComboBoxModel(BadgeOptions.TEMPLATES.keys.toList())
                    ).component
                    defaultTemplateCombo!!.selectedItem = settings.defaultTemplate
                }

                row("Default Position:") {
                    defaultGravityCombo = comboBox(
                        CollectionComboBoxModel(BadgeGravity.values().toList())
                    ).component
                    defaultGravityCombo!!.selectedItem = settings.defaultGravity
                }

                row("Default Font Size:") {
                    defaultFontSizeSpinner = spinner(8..100, 1)
                        .align(Align.FILL)
                        .component
                    defaultFontSizeSpinner!!.value = settings.defaultFontSize
                }

                row("Default Shape:") {
                    val shapeCombo = comboBox(
                        CollectionComboBoxModel(BadgeOptions.BadgeShape.values().toList())
                    ).component
                    shapeCombo.selectedItem = settings.defaultShape

                    shapeCombo.addActionListener {
                        settings.defaultShape = shapeCombo.selectedItem as BadgeOptions.BadgeShape
                        updatePreview()
                        modified = true
                    }
                }

                row("Default Border Radius:") {
                    defaultBorderRadiusSpinner = spinner(0..50, 1)
                        .align(Align.FILL)
                        .component
                    defaultBorderRadiusSpinner!!.value = settings.defaultBorderRadius
                }
            }

            group("Colors") {
                row("Background Color:") {
                    defaultBackgroundColorButton = ColorPickerButton(
                        settings.parseColor(settings.defaultBackgroundColor)
                    )
                    cell(defaultBackgroundColorButton!!)
                        .align(AlignX.FILL)
                }

                row("Text Color:") {
                    defaultTextColorButton = ColorPickerButton(
                        settings.parseColor(settings.defaultTextColor)
                    )
                    cell(defaultTextColorButton!!)
                        .align(AlignX.FILL)
                }

                row("Shadow Color:") {
                    defaultShadowColorButton = ColorPickerButton(
                        settings.parseColor(settings.defaultShadowColor)
                    )
                    cell(defaultShadowColorButton!!)
                        .align(AlignX.FILL)
                }
            }
        }
    }

    /**
     * Create the advanced settings panel.
     */
    private fun createAdvancedSettingsPanel(): DialogPanel {
        return panel {
            group("Performance") {
                row {
                    enableCachingCheckbox = checkBox("Enable image caching for better performance")
                        .bindSelected(settings::enableCaching)
                        .component
                }

                row("Maximum Cache Size:") {
                    maxCacheSizeSpinner = spinner(10..200, 10)
                        .align(Align.FILL)
                        .component
                    maxCacheSizeSpinner!!.value = settings.maxCacheSize
                    comment("Maximum number of images to keep in memory cache")
                }

                row {
                    button("Clear Caches") {
                        val badgeService = service<BadgeService>()
                        val imageService = service<ImageService>()
                        badgeService.clearCaches()
                        imageService.clearCaches()
                    }
                }
            }

            group("Integrations") {
                row {
                    integrateWithAndroidCheckbox = checkBox(
                        "Detect Android build variants (debug/release) for badge suggestions"
                    ).bindSelected(settings::integrateWithAndroidBuildVariants)
                        .component
                }

                row {
                    integrateWithGitCheckbox = checkBox(
                        "Use Git branch names for badge text suggestions"
                    ).bindSelected(settings::integrateWithGitBranches)
                        .component
                }
            }

            group("Recent Files") {
                row {
                    button("Clear Recent Settings") {
                        settings.recentlyUsedOptions.clear()
                        settings.recentFontPaths.clear()
                        modified = true
                    }
                }
            }
        }
    }

    /**
     * Create the custom templates panel.
     */
    private fun createTemplatesPanel(): JPanel {
        val panel = JPanel(BorderLayout())

        // Create table
        customTemplatesTable = JBTable(templatesTableModel)
        customTemplatesTable!!.selectionModel.selectionMode = ListSelectionModel.SINGLE_SELECTION
        customTemplatesTable!!.columnModel.getColumn(0).preferredWidth = 150
        customTemplatesTable!!.columnModel.getColumn(1).preferredWidth = 300

        val tableScrollPane = JBScrollPane(customTemplatesTable)
        panel.add(tableScrollPane, BorderLayout.CENTER)

        // Create buttons panel
        val buttonsPanel = panel {
            row {
                button("Add Template") {
                    val template = BadgeSettings.SerializableTemplate(
                        name = "New Template",
                        text = "SAMPLE"
                    )
                    settings.customTemplates.add(template)
                    templatesTableModel!!.fireTableDataChanged()
                    modified = true
                }

                button("Remove Template") {
                    val selectedRow = customTemplatesTable!!.selectedRow
                    if (selectedRow >= 0) {
                        settings.customTemplates.removeAt(selectedRow)
                        templatesTableModel!!.fireTableDataChanged()
                        modified = true
                    }
                }

                button("Edit Template") {
                    val selectedRow = customTemplatesTable!!.selectedRow
                    if (selectedRow >= 0) {
                        val template = settings.customTemplates[selectedRow]
                        // Edit template dialog would go here
                        // For now, just mark as modified
                        modified = true
                    }
                }
            }
        }

        panel.add(buttonsPanel, BorderLayout.SOUTH)

        // Add selection listener to update preview
        customTemplatesTable!!.selectionModel.addListSelectionListener {
            if (!it.valueIsAdjusting) {
                val selectedRow = customTemplatesTable!!.selectedRow
                if (selectedRow >= 0) {
                    val template = settings.customTemplates[selectedRow]
                    previewComponent?.updateOptions(template.toBadgeOptions())
                }
            }
        }

        return panel
    }

    /**
     * Create the preview panel.
     */
    private fun createPreviewPanel(): JPanel {
        val panel = JPanel(BorderLayout())
        panel.add(JBLabel("Preview:"), BorderLayout.NORTH)
        panel.add(previewComponent, BorderLayout.CENTER)
        return panel
    }

    /**
     * Set up change listeners for all UI components.
     */
    private fun setupChangeListeners() {
        // General settings change listeners
        defaultTemplateCombo!!.addActionListener {
            settings.defaultTemplate = defaultTemplateCombo!!.selectedItem as String
            updatePreview()
            modified = true
        }

        defaultGravityCombo!!.addActionListener {
            settings.defaultGravity = defaultGravityCombo!!.selectedItem as BadgeGravity
            updatePreview()
            modified = true
        }

        defaultFontSizeSpinner!!.addChangeListener {
            settings.defaultFontSize = defaultFontSizeSpinner!!.value as Int
            updatePreview()
            modified = true
        }

        defaultBorderRadiusSpinner!!.addChangeListener {
            settings.defaultBorderRadius = defaultBorderRadiusSpinner!!.value as Int
            updatePreview()
            modified = true
        }

        defaultBackgroundColorButton!!.addActionListener {
            settings.defaultBackgroundColor = colorToString(defaultBackgroundColorButton!!.selectedColor)
            updatePreview()
            modified = true
        }

        defaultTextColorButton!!.addActionListener {
            settings.defaultTextColor = colorToString(defaultTextColorButton!!.selectedColor)
            updatePreview()
            modified = true
        }

        defaultShadowColorButton!!.addActionListener {
            settings.defaultShadowColor = colorToString(defaultShadowColorButton!!.selectedColor)
            updatePreview()
            modified = true
        }

        // Performance settings
        enableCachingCheckbox!!.addActionListener {
            settings.enableCaching = enableCachingCheckbox!!.isSelected
            modified = true
        }

        maxCacheSizeSpinner!!.addChangeListener {
            settings.maxCacheSize = maxCacheSizeSpinner!!.value as Int
            modified = true
        }

        // Integration settings
        integrateWithAndroidCheckbox!!.addActionListener {
            settings.integrateWithAndroidBuildVariants = integrateWithAndroidCheckbox!!.isSelected
            modified = true
        }

        integrateWithGitCheckbox!!.addActionListener {
            settings.integrateWithGitBranches = integrateWithGitCheckbox!!.isSelected
            modified = true
        }
    }

    /**
     * Update the preview with current settings.
     */
    private fun updatePreview() {
        previewComponent?.updateOptions(getBadgePreviewOptions())
    }

    /**
     * Get badge options for the preview based on current settings.
     */
    private fun getBadgePreviewOptions(): BadgeOptions {
        return BadgeOptions(
            text = "PREVIEW",
            backgroundColor = defaultBackgroundColorButton!!.selectedColor,
            textColor = defaultTextColorButton!!.selectedColor,
            shadowColor = defaultShadowColorButton!!.selectedColor,
            fontSize = defaultFontSizeSpinner!!.value as Int,
            gravity = defaultGravityCombo!!.selectedItem as BadgeGravity,
            borderRadius = defaultBorderRadiusSpinner!!.value as Int,
            shape = settings.defaultShape
        )
    }

    /**
     * Convert a Color to a string representation.
     */
    private fun colorToString(color: java.awt.Color): String {
        return if (color.alpha == 255) {
            String.format("#%02X%02X%02X", color.red, color.green, color.blue)
        } else {
            String.format("rgba(%d,%d,%d,%.2f)",
                color.red, color.green, color.blue, color.alpha / 255.0f)
        }
    }

    override fun isModified(): Boolean = modified

    override fun apply() {
        // Apply all changes
        settings.defaultTemplate = defaultTemplateCombo!!.selectedItem as String
        settings.defaultGravity = defaultGravityCombo!!.selectedItem as BadgeGravity
        settings.defaultFontSize = defaultFontSizeSpinner!!.value as Int
        settings.defaultBorderRadius = defaultBorderRadiusSpinner!!.value as Int
        settings.defaultBackgroundColor = colorToString(defaultBackgroundColorButton!!.selectedColor)
        settings.defaultTextColor = colorToString(defaultTextColorButton!!.selectedColor)
        settings.defaultShadowColor = colorToString(defaultShadowColorButton!!.selectedColor)
        settings.enableCaching = enableCachingCheckbox!!.isSelected
        settings.maxCacheSize = maxCacheSizeSpinner!!.value as Int
        settings.integrateWithAndroidBuildVariants = integrateWithAndroidCheckbox!!.isSelected
        settings.integrateWithGitBranches = integrateWithGitCheckbox!!.isSelected

        modified = false
    }

    override fun reset() {
        // Reset UI to match settings
        defaultTemplateCombo!!.selectedItem = settings.defaultTemplate
        defaultGravityCombo!!.selectedItem = settings.defaultGravity
        defaultFontSizeSpinner!!.value = settings.defaultFontSize
        defaultBorderRadiusSpinner!!.value = settings.defaultBorderRadius
        defaultBackgroundColorButton!!.setSelectedColor(settings.parseColor(settings.defaultBackgroundColor))
        defaultTextColorButton!!.setSelectedColor(settings.parseColor(settings.defaultTextColor))
        defaultShadowColorButton!!.setSelectedColor(settings.parseColor(settings.defaultShadowColor))
        enableCachingCheckbox!!.isSelected = settings.enableCaching
        maxCacheSizeSpinner!!.value = settings.maxCacheSize
        integrateWithAndroidCheckbox!!.isSelected = settings.integrateWithAndroidBuildVariants
        integrateWithGitCheckbox!!.isSelected = settings.integrateWithGitBranches

        // Reload templates
        templatesTableModel!!.fireTableDataChanged()

        updatePreview()
        modified = false
    }

    /**
     * Table model for custom templates.
     */
    private class CustomTemplatesTableModel(private val templates: List<BadgeSettings.SerializableTemplate>) :
        AbstractTableModel() {

        override fun getRowCount(): Int = templates.size

        override fun getColumnCount(): Int = 2

        override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
            val template = templates[rowIndex]
            return when (columnIndex) {
                0 -> template.name
                1 -> template.text
                else -> ""
            }
        }

        override fun getColumnName(column: Int): String {
            return when (column) {
                0 -> "Template Name"
                1 -> "Badge Text"
                else -> ""
            }
        }
    }

    /**
     * Simple badge preview component.
     */
    class BadgePreviewComponent : JPanel() {
        private var options: BadgeOptions = BadgeOptions("PREVIEW")

        fun updateOptions(options: BadgeOptions) {
            this.options = options
            repaint()
        }

        override fun paintComponent(g: java.awt.Graphics) {
            super.paintComponent(g)
            val g2d = g as java.awt.Graphics2D

            // Draw background
            g2d.color = JBColor.LIGHT_GRAY
            g2d.fillRect(0, 0, width, height)

            // Draw badge in center
            val badgeWidth = options.text.length * options.fontSize / 2 + options.paddingX * 2
            val badgeHeight = options.fontSize + options.paddingY * 2

            g2d.setRenderingHint(
                java.awt.RenderingHints.KEY_ANTIALIASING,
                java.awt.RenderingHints.VALUE_ANTIALIAS_ON
            )

            val x = (width - badgeWidth) / 2
            val y = (height - badgeHeight) / 2

            // Draw badge background
            g2d.color = options.backgroundColor
            when (options.shape) {
                BadgeOptions.BadgeShape.RECTANGLE -> {
                    g2d.fillRect(x, y, badgeWidth, badgeHeight)
                }
                BadgeOptions.BadgeShape.ROUNDED_RECTANGLE -> {
                    g2d.fillRoundRect(
                        x, y, badgeWidth, badgeHeight,
                        options.borderRadius * 2, options.borderRadius * 2
                    )
                }
                BadgeOptions.BadgeShape.PILL -> {
                    g2d.fillRoundRect(
                        x, y, badgeWidth, badgeHeight,
                        badgeHeight, badgeHeight
                    )
                }
                BadgeOptions.BadgeShape.CIRCLE -> {
                    val diameter = Math.max(badgeWidth, badgeHeight)
                    g2d.fillOval(
                        (width - diameter) / 2,
                        (height - diameter) / 2,
                        diameter, diameter
                    )
                }
                BadgeOptions.BadgeShape.TRIANGLE -> {
                    val path = java.awt.geom.Path2D.Float()
                    path.moveTo((width / 2).toDouble(), y.toDouble())
                    path.lineTo((x + badgeWidth).toDouble(), (y + badgeHeight).toDouble())
                    path.lineTo(x.toDouble(), (y + badgeHeight).toDouble())
                    path.closePath()
                    g2d.fill(path)
                }
            }

            // Draw text
            g2d.color = options.textColor
            g2d.font = java.awt.Font("Dialog", java.awt.Font.BOLD, options.fontSize)

            val metrics = g2d.fontMetrics
            val textWidth = metrics.stringWidth(options.text)
            val textHeight = metrics.height

            g2d.drawString(
                options.text,
                (width - textWidth) / 2,
                (height - textHeight) / 2 + metrics.ascent
            )
        }
    }
}