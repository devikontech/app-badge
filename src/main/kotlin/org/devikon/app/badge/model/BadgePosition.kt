package org.devikon.app.badge.model

import java.awt.Point
import java.awt.Rectangle
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Represents the final position and rotation of a badge on an image.
 */
data class BadgePosition(
    val point: Point,
    val rotation: Int
)

/**
 * Utility functions for calculating badge positions.
 */
object BadgePositionCalculator {

    /**
     * Calculate the position of a badge on a circular/round image.
     *
     * @param containerWidth Width of the container image
     * @param containerHeight Height of the container image
     * @param badgeWidth Width of the badge
     * @param badgeHeight Height of the badge
     * @param circleRadius Radius of the circular content area
     * @param gravity Where to position the badge
     * @return Position and rotation for the badge
     */
    fun calculateCircularBadgePosition(
        containerWidth: Int,
        containerHeight: Int,
        badgeWidth: Int,
        badgeHeight: Int,
        circleRadius: Int,
        gravity: BadgeGravity
    ): BadgePosition {
        val angle = BadgeGravity.getPositioningAngle(gravity)
        val rotation = BadgeGravity.getRotationAngle(gravity)

        val radianAngle = Math.toRadians(angle.toDouble())

        // Calculate rotated badge dimensions
        val rotatedWidth = badgeWidth * abs(cos(radianAngle)) +
                badgeHeight * abs(sin(radianAngle))
        val rotatedHeight = badgeWidth * abs(sin(radianAngle)) +
                badgeHeight * abs(cos(radianAngle))

        // Calculate distance from center
        val distance = sqrt(
            0.0.coerceAtLeast(
                circleRadius.toDouble().pow(2.0) -
                        (badgeWidth / 2.0).pow(2.0)
            )
        )

        val trigAngle = Math.toRadians((angle - 90).toDouble())

        val x = (containerWidth / 2 -
                (distance - badgeHeight / 2) * cos(trigAngle) +
                -rotatedWidth / 2).toInt()

        val y = (containerHeight / 2 +
                (-distance + badgeHeight / 2) * sin(trigAngle) +
                -rotatedHeight / 2).toInt()

        return BadgePosition(Point(x, y), rotation)
    }

    /**
     * Calculate badge position based on manual position settings.
     *
     * @param containerWidth Width of the container image
     * @param containerHeight Height of the container image
     * @param badgeWidth Width of the badge
     * @param badgeHeight Height of the badge
     * @param position Manual position specification
     * @param gravity Gravity orientation for the badge
     * @return Position and rotation for the badge
     */
    fun calculateManualBadgePosition(
        containerWidth: Int,
        containerHeight: Int,
        badgeWidth: Int,
        badgeHeight: Int,
        position: BadgeOptions.Position,
        gravity: BadgeGravity
    ): BadgePosition {
        val rotation = BadgeGravity.getRotationAngle(gravity)
        val angle = BadgeGravity.getPositioningAngle(gravity)
        val radianAngle = Math.toRadians(angle.toDouble())

        // Calculate rotated badge dimensions
        val rotatedWidth = badgeWidth * abs(cos(radianAngle)) +
                badgeHeight * abs(sin(radianAngle))
        val rotatedHeight = badgeWidth * abs(sin(radianAngle)) +
                badgeHeight * abs(cos(radianAngle))

        val rotatedBadge = Rectangle(0, 0, rotatedWidth.toInt(), rotatedHeight.toInt())

        val point = if (position.y != null) {
            // If both x and y are provided, calculate absolute position
            Point(
                ((containerWidth - rotatedBadge.width) * position.x / 100.0).toInt(),
                ((containerHeight - rotatedBadge.height) * position.y / 100.0).toInt()
            )
        } else {
            // If only x is provided, calculate position along gravity axis
            when (gravity) {
                BadgeGravity.NORTH -> Point(
                    (containerWidth - rotatedBadge.width) / 2,
                    (containerHeight - rotatedBadge.height) * position.x / 100
                )

                BadgeGravity.NORTHEAST -> Point(
                    ((containerWidth - rotatedBadge.width) * (1 - position.x / 100.0)).toInt(),
                    ((containerHeight - rotatedBadge.height) * (position.x / 100.0)).toInt()
                )

                BadgeGravity.NORTHWEST -> Point(
                    ((containerWidth - rotatedBadge.width) * (position.x / 100.0)).toInt(),
                    ((containerHeight - rotatedBadge.height) * (position.x / 100.0)).toInt()
                )

                BadgeGravity.SOUTH -> Point(
                    (containerWidth - rotatedBadge.width) / 2,
                    ((containerHeight - rotatedBadge.height) * (1 - position.x / 100.0)).toInt()
                )

                BadgeGravity.SOUTHEAST -> Point(
                    ((containerWidth - rotatedBadge.width) * (1 - position.x / 100.0)).toInt(),
                    ((containerHeight - rotatedBadge.height) * (1 - position.x / 100.0)).toInt()
                )

                BadgeGravity.SOUTHWEST -> Point(
                    ((containerWidth - rotatedBadge.width) * (position.x / 100.0)).toInt(),
                    ((containerHeight - rotatedBadge.height) * (1 - position.x / 100.0)).toInt()
                )
            }
        }

        return BadgePosition(point, rotation)
    }
}