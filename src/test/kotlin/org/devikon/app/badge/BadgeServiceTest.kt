package org.devikon.app.badge

import org.devikon.app.badge.model.BadgeGravity
import org.devikon.app.badge.model.BadgeOptions
import org.devikon.app.badge.model.BadgePositionCalculator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BadgeServiceTest {

    @Test
    fun testCircularBadgePositionCalcSoutheast() {
        val containerWidth = 100
        val containerHeight = 100
        val badgeWidth = 20
        val badgeHeight = 10
        val circleRadius = 40

        val position = BadgePositionCalculator.calculateCircularBadgePosition(
            containerWidth,
            containerHeight,
            badgeWidth,
            badgeHeight,
            circleRadius,
            BadgeGravity.SOUTHEAST
        )

        // These values are approximate and might need adjustment based on the exact algorithm
        assertEquals(60, position.point.x)
        assertEquals(60, position.point.y)
        assertEquals(-45, position.rotation)
    }

    @Test
    fun testCircularBadgePositionCalcNortheast() {
        val containerWidth = 100
        val containerHeight = 100
        val badgeWidth = 20
        val badgeHeight = 10
        val circleRadius = 40

        val position = BadgePositionCalculator.calculateCircularBadgePosition(
            containerWidth,
            containerHeight,
            badgeWidth,
            badgeHeight,
            circleRadius,
            BadgeGravity.NORTHEAST
        )

        // These values are approximate and might need adjustment based on the exact algorithm
        assertEquals(60, position.point.x)
        assertEquals(26, position.point.y)
        assertEquals(45, position.rotation)
    }

    @Test
    fun testManualBadgePositionCalcWithXPosition() {
        val containerWidth = 100
        val containerHeight = 100
        val badgeWidth = 20
        val badgeHeight = 10

        val position = BadgePositionCalculator.calculateManualBadgePosition(
            containerWidth,
            containerHeight,
            badgeWidth,
            badgeHeight,
            BadgeOptions.Position(50), // 50% along the axis
            BadgeGravity.SOUTH
        )

        assertEquals(40, position.point.x) // (100-20)/2
        assertEquals(45, position.point.y) // (100-10)*(50/100)
        assertEquals(0, position.rotation)
    }

    @Test
    fun testManualBadgePositionCalcWithXYPosition() {
        val containerWidth = 100
        val containerHeight = 100
        val badgeWidth = 20
        val badgeHeight = 10

        val position = BadgePositionCalculator.calculateManualBadgePosition(
            containerWidth,
            containerHeight,
            badgeWidth,
            badgeHeight,
            BadgeOptions.Position(30, 60), // 30% from left, 60% from top
            BadgeGravity.NORTHEAST // gravity only affects rotation in this case
        )

        assertEquals(24, position.point.x) // (100-20)*(30/100)
        assertEquals(54, position.point.y) // (100-10)*(60/100)
        assertEquals(45, position.rotation)
    }
}