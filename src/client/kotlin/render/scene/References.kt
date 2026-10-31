package org.lain.engine.client.render.scene

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import org.lain.cyberia.ecs.Component
import org.lain.engine.client.mc.MinecraftClient
import org.lain.engine.client.resources.MeshModel
import org.lain.engine.item.UNDEFINED_MODEL_ID
import org.lain.engine.mc.engineId
import org.lain.engine.util.ecs.EntityId

typealias ResourcePath = String

@Serializable
class PropModelReference(val path: ResourcePath) {
    @Transient private var model: MeshModel? = null

    fun resolve(): MeshModel {
        return model ?: run {
            val location = engineId(path)
            val resolved = itemModelManager.getModel(location) ?: itemModelManager.getModel(UNDEFINED_MODEL_ID)
            resolved as MeshModel
            resolved.also { model = it }
        }
    }

    companion object {
        private val itemModelManager = MinecraftClient.modelManager
    }
}

interface RenderEntityReferences : Component {
    fun entityReferences(): List<EntityId> = emptyList()
    fun remapEntityReferences(remap: (EntityId) -> EntityId): Component
}

// Retain the JSON shape of the former RenderReference while runtime fields use EntityId directly.
object RenderEntityIdSerializer : KSerializer<EntityId> {
    private val serializer = SerializedRenderEntityId.serializer()
    override val descriptor = serializer.descriptor

    override fun serialize(encoder: Encoder, value: EntityId) =
        encoder.encodeSerializableValue(serializer, SerializedRenderEntityId(value))

    override fun deserialize(decoder: Decoder): EntityId =
        decoder.decodeSerializableValue(serializer).id
}

@Serializable
private data class SerializedRenderEntityId(val id: EntityId)
