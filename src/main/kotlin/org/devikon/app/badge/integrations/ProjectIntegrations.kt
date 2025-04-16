package org.devikon.app.badge.integrations

import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.VcsException
import org.devikon.app.badge.settings.BadgeSettings
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths

/**
 * Project integration utilities for enhancing badge generation with project-specific information.
 */
object ProjectIntegrations {
    private val logger = Logger.getInstance(ProjectIntegrations::class.java)

    /**
     * Get suggestions for badge text based on project context.
     */
    fun getSuggestedBadgeText(project: Project): String {
        val settings = service<BadgeSettings>()
        var badgeText = "DEV"

        try {
            // Check if Git integration is enabled
            if (settings.integrateWithGitBranches) {
                val gitBranch = getCurrentGitBranch(project)
                if (!gitBranch.isNullOrBlank()) {
                    badgeText = gitBranchToBadgeText(gitBranch)
                }
            }

            // Check if Android integration is enabled
            if (settings.integrateWithAndroidBuildVariants) {
                val androidVariant = detectAndroidBuildVariant(project)
                if (androidVariant != null) {
                    // Android variant takes precedence
                    badgeText = androidVariant
                }
            }
        } catch (e: Exception) {
            logger.warn("Error getting suggested badge text", e)
        }

        return badgeText
    }

    /**
     * Get the current Git branch name.
     */
    fun getCurrentGitBranch(project: Project): String? {
        val vcsManager = ProjectLevelVcsManager.getInstance(project)
            ?: return null

        val vcsRoots = vcsManager.allVcsRoots
        if (vcsRoots.isEmpty()) return null

        for (root in vcsRoots) {
            val vcs = root.vcs ?: continue
            if (vcs.name.lowercase().contains("git")) {
                try {
                    // Try to get git branch from .git/HEAD file
                    val gitDir = File("${root.path}/.git")
                    if (gitDir.exists() && gitDir.isDirectory) {
                        val headFile = File(gitDir, "HEAD")
                        if (headFile.exists() && headFile.isFile) {
                            val headContent = headFile.readText().trim()
                            if (headContent.startsWith("ref: refs/heads/")) {
                                return headContent.removePrefix("ref: refs/heads/")
                            }
                        }
                    }

                    // Try using VCS API as fallback
                    val vcsProvider = vcs.vcsHistoryProvider ?: continue
                    val file = root.vcs
                    // TODO:: Fix this
//                    val session = vcsProvider.createSessionFor(vcs.project.basePath)
//                        ?: continue

                    // Different ways to try to get branch name
                    return null  // Would require deeper Git integration
                } catch (e: VcsException) {
                    logger.warn("Error getting Git branch", e)
                }
            }
        }

        return null
    }

    /**
     * Convert a Git branch name to a suitable badge text.
     */
    private fun gitBranchToBadgeText(branchName: String): String {
        // Convert common branch naming patterns to badge text
        return when {
            branchName == "main" || branchName == "master" -> "PROD"
            branchName.startsWith("release/") -> branchName.removePrefix("release/").uppercase()
            branchName.startsWith("feature/") -> "DEV"
            branchName.contains("dev", ignoreCase = true) -> "DEV"
            branchName.contains("alpha", ignoreCase = true) -> "ALPHA"
            branchName.contains("beta", ignoreCase = true) -> "BETA"
            branchName.contains("fix", ignoreCase = true) -> "FIX"
            branchName.contains("hotfix", ignoreCase = true) -> "HOTFIX"
            branchName.contains("stage", ignoreCase = true) -> "STAGING"
            branchName.contains("test", ignoreCase = true) -> "TEST"
            branchName.contains("rc", ignoreCase = true) -> "RC"
            else -> branchName.take(5).uppercase() // Truncate and uppercase
        }
    }

    /**
     * Detect Android build variant from project structure.
     * Returns the detected variant name or null if not detected.
     */
    fun detectAndroidBuildVariant(project: Project): String? {
        val projectDir = project.basePath ?: return null

        // Check common Android project structures
        val androidManifestPaths = listOf(
            "AndroidManifest.xml",
            "app/src/main/AndroidManifest.xml",
            "src/main/AndroidManifest.xml"
        )

        var isAndroidProject = false

        for (manifestPath in androidManifestPaths) {
            val manifestFile = File(projectDir, manifestPath)
            if (manifestFile.exists()) {
                isAndroidProject = true
                break
            }
        }

        if (!isAndroidProject) return null

        // Check for build variant indications
        val gradleFiles = listOf(
            "app/build.gradle",
            "app/build.gradle.kts",
            "build.gradle",
            "build.gradle.kts"
        )

        for (gradleFile in gradleFiles) {
            val file = File(projectDir, gradleFile)
            if (file.exists()) {
                try {
                    val content = file.readText()

                    // Check for build types and flavors
                    when {
                        content.contains("buildTypes {") && content.contains("debug {") -> {
                            // If build.gradle has debug configuration
                            return "DEBUG"
                        }

                        content.contains("flavor {") && content.contains("alpha {") -> {
                            return "ALPHA"
                        }

                        content.contains("flavor {") && content.contains("beta {") -> {
                            return "BETA"
                        }

                        content.contains("flavor {") && content.contains("staging {") -> {
                            return "STAGING"
                        }

                        content.contains("applicationIdSuffix \".debug\"") -> {
                            return "DEBUG"
                        }
                    }
                } catch (e: Exception) {
                    logger.warn("Error reading gradle file: ${file.path}", e)
                }
            }
        }

        // Look for build directories that might indicate the current variant
        val buildDirs = listOf(
            "app/build/intermediates/res/debug",
            "app/build/intermediates/res/staging",
            "app/build/intermediates/res/beta",
            "app/build/intermediates/res/alpha"
        )

        for (buildDir in buildDirs) {
            val dir = File(projectDir, buildDir)
            if (dir.exists() && dir.isDirectory) {
                // Extract variant name from path
                val variantName = dir.name.uppercase()
                return variantName
            }
        }

        // Default to generic Android marker if we can't determine specific variant
        return "DEBUG"
    }

    /**
     * Analyze the project structure to determine the most appropriate badge type.
     */
    fun analyzeProjectStructure(project: Project): BadgeRecommendation {
        val projectDir = project.basePath ?: return BadgeRecommendation("DEV", "Default")

        // Check for Android project
        if (isAndroidProject(projectDir)) {
            val variant = detectAndroidBuildVariant(project) ?: "DEBUG"
            return when (variant) {
                "DEBUG" -> BadgeRecommendation("DEBUG", "Development")
                "ALPHA" -> BadgeRecommendation("ALPHA", "Alpha")
                "BETA" -> BadgeRecommendation("BETA", "Beta")
                "STAGING" -> BadgeRecommendation("STAGING", "Staging")
                else -> BadgeRecommendation(variant, "Development")
            }
        }

        // Check for iOS project
        if (isIosProject(projectDir)) {
            return BadgeRecommendation("iOS", "Development")
        }

        // Check for web projects
        if (isWebProject(projectDir)) {
            return BadgeRecommendation("WEB", "Version Tag")
        }

        // Use Git branch if available
        val gitBranch = getCurrentGitBranch(project)
        if (!gitBranch.isNullOrBlank()) {
            val badgeText = gitBranchToBadgeText(gitBranch)

            // Map badge text to template
            val template = when (badgeText) {
                "ALPHA" -> "Alpha"
                "BETA" -> "Beta"
                "DEV" -> "Development"
                "TEST" -> "Testing"
                "STAGING" -> "Staging"
                "PROD" -> "Minimal"
                else -> "Default"
            }

            return BadgeRecommendation(badgeText, template)
        }

        // Default recommendation
        return BadgeRecommendation("DEV", "Default")
    }

    /**
     * Check if the project is an Android project.
     */
    private fun isAndroidProject(projectDir: String): Boolean {
        val androidIndicators = listOf(
            "AndroidManifest.xml",
            "app/src/main/AndroidManifest.xml",
            "build.gradle",
            "app/build.gradle",
            "build.gradle.kts",
            "app/build.gradle.kts"
        )

        return androidIndicators.any {
            Files.exists(Paths.get(projectDir, it))
        }
    }

    /**
     * Check if the project is an iOS project.
     */
    private fun isIosProject(projectDir: String): Boolean {
        val iosIndicators = listOf(
            "Info.plist",
            "AppDelegate.swift",
            ".xcodeproj",
            "Podfile"
        )

        return iosIndicators.any {
            Files.exists(Paths.get(projectDir, it))
        }
    }

    /**
     * Check if the project is a web project.
     */
    private fun isWebProject(projectDir: String): Boolean {
        val webIndicators = listOf(
            "package.json",
            "webpack.config.js",
            "angular.json",
            "next.config.js",
            "vite.config.js",
            "index.html"
        )

        return webIndicators.any {
            Files.exists(Paths.get(projectDir, it))
        }
    }

    /**
     * Recommendation for badge text and template.
     */
    data class BadgeRecommendation(
        val badgeText: String,
        val templateName: String
    )
}