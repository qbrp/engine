package org.lain.engine.test

import net.minecraft.SharedConstants
import net.minecraft.server.Bootstrap
import net.minecraft.world.SimpleContainer
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.lain.engine.mc.ecs.moveItemStack

class MinecraftPlayerInventoryTest {
    companion object {
        @JvmStatic
        @BeforeAll
        fun bootstrapMinecraft() {
            SharedConstants.tryDetectVersion()
            Bootstrap.bootStrap()
        }
    }

    @Test
    fun movesOriginalStackWithoutLosingCount() {
        val source = SimpleContainer(1)
        val target = SimpleContainer(1)
        val itemStack = ItemStack(Items.STICK, 8)
        source.setItem(0, itemStack)

        assertTrue(moveItemStack(source, target, itemStack, 0))
        assertTrue(source.getItem(0).isEmpty)
        assertSame(itemStack, target.getItem(0))
        assertEquals(8, target.getItem(0).count)
    }

    @Test
    fun leavesInventoriesUnchangedWhenTargetSlotIsOccupied() {
        val source = SimpleContainer(1)
        val target = SimpleContainer(1)
        val itemStack = ItemStack(Items.STICK, 8)
        val occupiedStack = ItemStack(Items.STONE)
        source.setItem(0, itemStack)
        target.setItem(0, occupiedStack)

        assertFalse(moveItemStack(source, target, itemStack, 0))
        assertSame(itemStack, source.getItem(0))
        assertSame(occupiedStack, target.getItem(0))
    }
}
