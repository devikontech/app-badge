package org.devikon.app.badge.ui

import java.awt.Color
import java.awt.Dimension
import java.awt.Graphics
import javax.swing.JButton
import javax.swing.JColorChooser

/**
 * A button that displays a color and opens a color chooser dialog when clicked.
 */
class ColorPickerButton(initialColor: Color) : JButton() {

    var selectedColor: Color = initialColor
        private set

    init {
        preferredSize = Dimension(40, 20)
        addActionListener {
            val newColor = JColorChooser.showDialog(
                this,
                "Choose Color",
                selectedColor
            )
            if (newColor != null) {
                selectedColor = newColor
                repaint()
            }
        }
    }

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)

        // Fill with selected color
        g.color = selectedColor
        g.fillRect(5, 5, width - 10, height - 10)

        // Draw border
        g.color = Color.BLACK
        g.drawRect(5, 5, width - 10, height - 10)
    }

    fun setSelectedColor(color: Color) {
        selectedColor = color
        repaint()
    }
}