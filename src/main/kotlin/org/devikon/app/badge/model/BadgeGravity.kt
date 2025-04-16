package org.devikon.app.badge.model

/**
 * Defines the possible positions for a badge on an image.
 */
enum class BadgeGravity(val displayName: String) {
    NORTHWEST("Northwest"),
    NORTH("North"),
    NORTHEAST("Northeast"),
    SOUTHWEST("Southwest"),
    SOUTH("South"),
    SOUTHEAST("Southeast");

    companion object {
        /**
         * Get the rotation angle in degrees for each gravity position
         */
        fun getRotationAngle(gravity: BadgeGravity): Int = when (gravity) {
            NORTH -> 0
            NORTHEAST -> 45
            NORTHWEST -> -45
            SOUTH -> 0
            SOUTHEAST -> -45
            SOUTHWEST -> 45
        }

        /**
         * Get the positioning angle for calculating badge placement
         */
        fun getPositioningAngle(gravity: BadgeGravity): Int = when (gravity) {
            NORTH -> 180
            NORTHEAST -> -135
            NORTHWEST -> 135
            SOUTH -> 0
            SOUTHEAST -> -45
            SOUTHWEST -> 45
        }
    }

    override fun toString(): String = displayName
}