package org.lain.engine.test.lua

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.lain.cyberia.ecs.setComponent
import org.lain.engine.data.PersistentIdComponent
import org.lain.engine.data.persistentId
import org.lain.engine.player.interaction.Interactor
import org.lain.engine.script.ComponentInspectionResult
import org.lain.engine.script.CoreScriptComponents
import org.lain.engine.script.inspectionSnapshot
import org.lain.engine.script.lua.library.ecs.pushInteractionComponents
import org.lain.engine.test.DummyWorld
import org.lain.engine.util.property.PropertyPathDto
import org.lain.engine.util.property.PropertyScalar
import org.lain.engine.util.property.PropertyValue

class LuaEntityInspectionTest : LuaScriptEngineTestBase() {
    @Test
    fun interactorOwnerIsInspectedAsEntityReference() {
        val world = DummyWorld()
        val owner = world.addEntity()
        val interactor = world.addEntity()
        val ownerId = persistentId("test-owner")
        with(world) {
            owner.setComponent(PersistentIdComponent(ownerId))
            interactor.setComponent(Interactor(owner))
        }
        val lua = createLuaEngine("lua/component_state.lua")
        lua.loadWorld(world)
        with(lua) { world.pushInteractionComponents() }

        val snapshot = with(world) { interactor.inspectionSnapshot() }
        val component = snapshot.data.components.single { it.type == CoreScriptComponents.INTERACTOR.id }
        val tree = (component.data as ComponentInspectionResult.Value).tree
        val ownerKey = PropertyValue.Primitive(PropertyScalar.Str("owner"))
        val ownerPath = PropertyPathDto(listOf(PropertyPathDto.Segment.EntryValue(ownerKey)))

        assertEquals(
            PropertyValue.Primitive(PropertyScalar.EntityRef(owner, ownerId, null)),
            tree.properties.getValue(ownerPath).value,
        )
        assertEquals(1, tree.compounds.size)
    }
}
