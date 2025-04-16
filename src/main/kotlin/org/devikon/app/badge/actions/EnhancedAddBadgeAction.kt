package org.devikon.app.badge.actions

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.devikon.app.badge.services.BadgeService
import org.devikon.app.badge.services.ImageService
import org.devikon.app.badge.ui.EnhancedAddBadgeDialog
import java.io.File
import java.util.concurrent.atomic.AtomicReference

/**
 * Enhanced action to add a badge to image files with advanced features.
 */
class EnhancedAddBadgeAction : AnAction() {
    private val badgeService = service<BadgeService>()
    private val imageService = service<ImageService>()

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return

        if (file.isDirectory) {
            processBatchDirectory(project, file)
        } else if (isImageFile(file)) {
            processSingleImage(project, file)
        }
    }

    override fun update(e: AnActionEvent) {
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE)

        // Enable for directories or image files
        e.presentation.isEnabledAndVisible = file != null &&
                (file.isDirectory || isImageFile(file))
    }

    /**
     * Process a single image file.
     */
    private fun processSingleImage(project: Project, file: VirtualFile) {
        val dialog = EnhancedAddBadgeDialog(project, file.name, file)

        if (dialog.showAndGet()) {
            val options = dialog.getBadgeOptions()

            // Process the file in a background task
            ProgressManager.getInstance().run(object : Task.Backgroundable(
                project,
                "Adding Badge to ${file.name}",
                false
            ) {
                override fun run(indicator: ProgressIndicator) {
                    indicator.isIndeterminate = true

                    val ioFile = File(file.path)
                    val success = badgeService.addBadgeToImage(ioFile, options)

                    if (success) {
                        // Refresh the VFS to show the updated image
                        file.refresh(false, false)

                        showNotification(
                            project,
                            "Badge added to ${file.name}",
                            NotificationType.INFORMATION
                        )
                    } else {
                        showNotification(
                            project,
                            "Failed to add badge to ${file.name}",
                            NotificationType.ERROR
                        )
                    }
                }
            })
        }
    }

    /**
     * Process multiple images in a directory.
     */
    private fun processBatchDirectory(project: Project, directory: VirtualFile) {
        // First, analyze the directory to find image files
        val imageFilesRef = AtomicReference<List<VirtualFile>>()

        ReadAction.run<Exception> {
            val imageFiles = directory.children
                .filter { !it.isDirectory && isImageFile(it) }
            imageFilesRef.set(imageFiles)
        }

        val imageFiles = imageFilesRef.get()

        if (imageFiles.isEmpty()) {
            showNotification(
                project,
                "No image files found in ${directory.name}",
                NotificationType.INFORMATION
            )
            return
        }

        // Show dialog with the directory name
        val dialog = EnhancedAddBadgeDialog(
            project,
            "${imageFiles.size} images in ${directory.name}"
        )

        if (dialog.showAndGet()) {
            val options = dialog.getBadgeOptions()

            // Process files in a background task
            ProgressManager.getInstance().run(object : Task.Backgroundable(
                project,
                "Adding Badges to ${imageFiles.size} Images",
                true
            ) {
                override fun run(indicator: ProgressIndicator) {
                    indicator.isIndeterminate = false

                    val ioFiles = imageFiles.map { File(it.path) }
                    var processed = 0

                    // Process in batches for better progress indication
                    val batchSize = 5
                    val batches = ioFiles.chunked(batchSize)

                    batches.forEachIndexed { batchIndex, batch ->
                        indicator.fraction = batchIndex.toDouble() / batches.size
                        indicator.text = "Processing batch ${batchIndex + 1}/${batches.size}"

                        batch.forEachIndexed { index, file ->
                            indicator.text2 = "Processing ${file.name}"

                            if (badgeService.addBadgeToImage(file, options)) {
                                processed++
                            }

                            if (indicator.isCanceled) {
                                return@run
                            }
                        }
                    }

                    // Refresh the directory to show updated images
                    directory.refresh(true, true)

                    // Show results
                    showNotification(
                        project,
                        "Added badges to $processed out of ${ioFiles.size} images in ${directory.name}",
                        NotificationType.INFORMATION
                    )
                }
            })
        }
    }

    /**
     * Check if a file is an image file based on its extension.
     */
    private fun isImageFile(file: VirtualFile): Boolean {
        val extension = file.extension?.lowercase() ?: return false
        return extension in listOf("png", "jpg", "jpeg", "gif", "bmp", "webp")
    }

    /**
     * Show a notification to the user.
     */
    private fun showNotification(project: Project, content: String, type: NotificationType) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("Badge Plugin Notifications")
            .createNotification(content, type)
            .notify(project)
    }
}