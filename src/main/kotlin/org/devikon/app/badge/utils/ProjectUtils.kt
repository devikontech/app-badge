package org.devikon.app.badge.utils

import com.intellij.openapi.vfs.VirtualFile
import java.io.File

/**
 * Utility functions for working with projects and virtual files.
 */
object ProjectUtils {

    /**
     * Find all files matching a glob pattern in a directory.
     */
    fun findFilesMatchingPattern(directory: VirtualFile, pattern: String): List<File> {
        val patternRegex = globToRegex(pattern)
        return findFilesRecursively(directory)
            .filter { patternRegex.matches(it.name) }
            .map { File(it.path) }
    }

    /**
     * Find all files in a directory and its subdirectories.
     */
    private fun findFilesRecursively(directory: VirtualFile): List<VirtualFile> {
        val result = mutableListOf<VirtualFile>()

        directory.children.forEach { child ->
            if (child.isDirectory) {
                result.addAll(findFilesRecursively(child))
            } else {
                result.add(child)
            }
        }

        return result
    }

    /**
     * Convert a glob pattern to a regex pattern.
     */
    private fun globToRegex(pattern: String): Regex {
        val regexPattern = pattern
            .replace(".", "\\.")
            .replace("*", ".*")
            .replace("?", ".")

        return Regex("^$regexPattern$")
    }

    /**
     * Determine if a file is an image file based on its extension.
     */
    fun isImageFile(file: VirtualFile): Boolean {
        val extension = file.extension?.lowercase() ?: return false
        return extension in listOf("png", "jpg", "jpeg", "gif", "bmp", "webp")
    }

    /**
     * Get all image files in a directory (non-recursive).
     */
    fun getImageFilesInDirectory(directory: VirtualFile): List<VirtualFile> {
        return directory.children.filter { !it.isDirectory && isImageFile(it) }
    }
}