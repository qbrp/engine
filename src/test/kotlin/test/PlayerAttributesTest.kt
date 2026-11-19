package org.lain.engine.test

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.lain.engine.player.CustomPlayerAttributes
import org.lain.engine.player.PlayerAttributes
import org.lain.engine.player.withCustom

class PlayerAttributesTest {
    @Test
    fun customAttributesOverrideBaseValues() {
        val base = PlayerAttributes(
            speed = 0.1f,
            jumpStrength = 0.2f,
            gravity = 0.3f,
            flyingSpeed = 0.4f,
        )
        val custom = CustomPlayerAttributes(
            speed = 1.1f,
            jumpStrength = 1.2f,
            gravity = 1.3f,
        )

        assertEquals(
            PlayerAttributes(
                speed = 1.1f,
                jumpStrength = 1.2f,
                gravity = 1.3f,
                flyingSpeed = 0.4f,
            ),
            base.withCustom(custom)
        )
    }

    @Test
    fun absentCustomValuesKeepBaseValues() {
        val base = PlayerAttributes()

        assertEquals(base, base.withCustom(null))
        assertEquals(base, base.withCustom(CustomPlayerAttributes()))
    }
}
