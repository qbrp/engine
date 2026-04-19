package org.lain.engine.mc

import com.mojang.brigadier.arguments.BoolArgumentType
import com.mojang.brigadier.arguments.DoubleArgumentType
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.ArgumentBuilder
import net.minecraft.entity.Entity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.entity.projectile.ProjectileUtil
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.hit.EntityHitResult
import net.minecraft.util.hit.HitResult
import net.minecraft.world.RaycastContext
import org.lain.engine.player.EnginePlayer
import org.lain.engine.player.SOCIAL_INTERACTION_DISTANCE
import org.lain.engine.script.IntentBehaviour
import org.lain.engine.script.ScriptContext
import org.lain.engine.server.ServerHandler
import org.lain.engine.util.*

fun ServerCommandDispatcher.registerIntentCommands(
    contents: NamespacedStorage,
    intents: List<Intent> = contents.intents.values.toList(),
    handler: ServerHandler
) = intents.forEach {
    val (id, name, script, inputs) = it
    val node = literal(id.value.substringAfterLast('/'))
    var current: ArgumentBuilder<ServerCommandSource, *> = node

    inputs.forEachIndexed { index, (inputId, inputType) ->
        val argType = when (inputType) {
            Input.Type.Logic -> BoolArgumentType.bool()
            Input.Type.Double -> DoubleArgumentType.doubleArg()
            Input.Type.Integer -> IntegerArgumentType.integer()
            Input.Type.Table -> if (index == inputs.lastIndex) StringArgumentType.greedyString() else StringArgumentType.string()
            is Input.Type.Text -> if (inputType.isSingleWord) StringArgumentType.word() else if (index == inputs.lastIndex) StringArgumentType.greedyString() else StringArgumentType.string()
        }

        val next = argument(inputId, argType)
        current.then(next)
        current = next
    }

    current.executeCatching { ctx -> executeIntent(it, ctx.getIntentScriptContext(inputs), contents, handler) }
    register(node)
}

fun Context.getIntentScriptContext(inputs: List<AnyInput>): ScriptContext.IntentExecution {
    val enginePlayer = requirePlayer()
    return ScriptContext.IntentExecution(
        IntentActor(
            IntentActor.Type.COMMAND,
            enginePlayer,
            enginePlayer.entityId,
        ),
        null,
        inputs
            .filter { input -> command.nodes.find { it.node.name == input.id } != null }
            .map { input -> (input as Input<Any>).valueOf(command.getArgument(input.id, input.type.kclass.java)) },
        CommandIntentBehaviour(requireEntity())
    )
}

private val Input.Type<*>.kclass
    get() = when (this) {
        Input.Type.Logic -> Boolean::class
        Input.Type.Integer -> Int::class
        Input.Type.Double -> Double::class
        Input.Type.Table -> String::class
        is Input.Type.Text -> String::class
    }

fun ClientCommandIntentBehaviour(player: EnginePlayer): CommandIntentBehaviour {
    val table by injectEntityTable()
    return CommandIntentBehaviour(table.client.getEntity(player.id)!!)
}

class CommandIntentBehaviour(private val entity: Entity) : IntentBehaviour {
    override fun generateTarget(): IntentTarget {
        val playerTable by injectEntityTable()
        return when(val result = raycastPlayerOrBlock(
            entity,
            SOCIAL_INTERACTION_DISTANCE.toDouble(),
            0f)
        ) {
            is BlockHitResult -> IntentTarget(null, result.blockPos.engine(), result.pos.engine())
            is EntityHitResult -> IntentTarget(playerTable.getGeneralPlayer(result.entity as PlayerEntity), result.entity.blockPos.engine(), result.pos.engine())
            else -> error("Unexpected raycast hit result type ${result.type}")
        }
    }
}

private fun raycastPlayerOrBlock(
    source: Entity,
    maxDistance: Double,
    tickDelta: Float
): HitResult = with(source.entityWorld) {
    val start = source.getCameraPosVec(tickDelta)
    val direction = source.getRotationVec(tickDelta)
    val end = start.add(direction.multiply(maxDistance))

    val blockHit = raycast(
        RaycastContext(
            start,
            end,
            RaycastContext.ShapeType.OUTLINE,
            RaycastContext.FluidHandling.NONE,
            source
        )
    )

    val searchBox = source.boundingBox
        .stretch(direction.multiply(maxDistance))
        .expand(1.0)

    val entityHit = ProjectileUtil.getEntityCollision(
        this,
        source,
        start,
        end,
        searchBox,
        { entity -> entity is PlayerEntity && entity != source },
        0f
    )

    if (entityHit != null) {
        val entityDist = start.squaredDistanceTo(entityHit.pos)
        if (blockHit.type == HitResult.Type.MISS) {
            return entityHit
        }
        val blockDist = start.squaredDistanceTo(blockHit.pos)
        if (entityDist < blockDist) {
            return entityHit
        }
    }

    return blockHit
}