package org.devikon.app.badge.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory

/**
 * Factory for creating the Badge Generator tool window.
 */
class BadgeGeneratorToolWindowFactory : ToolWindowFactory {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val badgeGeneratorPanel = BadgeGeneratorToolWindowPanel(project)
        val contentFactory = ContentFactory.getInstance()
        val content = contentFactory.createContent(badgeGeneratorPanel, "Generator", false)
        toolWindow.contentManager.addContent(content)
    }
}