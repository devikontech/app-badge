package org.devikon.app.badge.listeners

import com.intellij.ide.AppLifecycleListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ProjectManagerListener
import org.devikon.app.badge.services.BadgeService
import org.devikon.app.badge.services.ImageService
import java.util.concurrent.ConcurrentHashMap

/**
 * Plugin lifecycle listener for initialization and cleanup.
 */
class BadgePluginStartupListener : AppLifecycleListener, ProjectManagerListener {
    private val logger = Logger.getInstance(BadgePluginStartupListener::class.java)
    private val openProjects = ConcurrentHashMap.newKeySet<String>()

    override fun appStarted() {
        logger.info("Badge Generator plugin initialized")

        try {
            // Pre-load services to ensure they're initialized
            val badgeService = service<BadgeService>()
            val imageService = service<ImageService>()

            // Preload default font during startup for better performance
            ApplicationManager.getApplication().executeOnPooledThread {
                try {
                    badgeService.loadDefaultFont()
                    logger.info("Default font preloaded")
                } catch (e: Exception) {
                    logger.warn("Failed to preload default font", e)
                }
            }

            // Register for project events
            val projectManager = ProjectManager.getInstance()
            projectManager.addProjectManagerListener { project ->
                // Track opened projects
                openProjects.add(project.locationHash)

                logger.info("Project opened: ${project.name}")

                project.isInitialized
            }
        } catch (e: Exception) {
            logger.error("Error during plugin initialization", e)
        }
    }

    override fun appWillBeClosed(isRestart: Boolean) {
        logger.info("Badge Generator plugin shutting down")

        try {
            // Clean up resources
            val imageService = service<ImageService>()
            imageService.clearCaches()

            // Unregister project listener
            val projectManager = ProjectManager.getInstance()
            projectManager.removeProjectManagerListener { project ->
                // Remove from tracking
                openProjects.remove(project.locationHash)

                logger.info("Project closed: ${project.name}")

                project.isDisposed
            }
        } catch (e: Exception) {
            logger.error("Error during plugin shutdown", e)
        }
    }


//    override fun projectOpened(project: Project) {
//        // Track opened projects
//        openProjects.add(project.locationHash)
//
//        logger.info("Project opened: ${project.name}")
//
//        // Check if this is an Android project and offer badge configuration
//        ApplicationManager.getApplication().executeOnPooledThread {
//            try {
//                analyzeProjectOnOpen(project)
//            } catch (e: Exception) {
//                logger.warn("Error analyzing project on open: ${project.name}", e)
//            }
//        }
//    }

    override fun projectClosed(project: Project) {
        // Remove from tracking
        openProjects.remove(project.locationHash)

        logger.info("Project closed: ${project.name}")
    }

    /**
     * Analyze project on open for potential badge needs.
     */
    private fun analyzeProjectOnOpen(project: Project) {
        // This would analyze the project for mobile app resources, build variants, etc.
        // For now, we'll leave this as a placeholder for future implementation
        // Could show notification offering to configure badges for detected app icons
    }

    /**
     * Check if we should show badge-related notifications for this project.
     */
    private fun shouldShowBadgeNotifications(project: Project): Boolean {
        val basePath = project.basePath ?: return false

        // Check if this is likely a project that would benefit from badges
        return basePath.contains("android", ignoreCase = true) ||
                basePath.contains("ios", ignoreCase = true) ||
                basePath.contains("mobile", ignoreCase = true) ||
                basePath.contains("app", ignoreCase = true)
    }
}