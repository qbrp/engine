package org.lain.engine.mc.commands

import com.mojang.brigadier.arguments.BoolArgumentType
import com.mojang.brigadier.arguments.DoubleArgumentType
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.ArgumentBuilder
import com.sk89q.worldedit.WorldEdit
import com.sk89q.worldedit.fabric.FabricAdapter
import com.sk89q.worldedit.math.BlockVector3
import net.minecraft.commands.CommandSourceStack
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.entity.projectile.ProjectileUtil
import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.EntityHitResult
import net.minecraft.world.phys.HitResult
import org.lain.engine.mc.engine
import org.lain.engine.mc.isWorldEditAvailable
import org.lain.engine.mc.voxelPos
import org.lain.engine.player.EnginePlayer
import org.lain.engine.player.SOCIAL_INTERACTION_DISTANCE
import org.lain.engine.script.ExecutionResult
import org.lain.engine.script.IntentBehaviour
import org.lain.engine.script.NamespacedStorageAccess
import org.lain.engine.script.ScriptContext
import org.lain.engine.server.ServerHandler
import org.lain.engine.util.*
import org.lain.engine.world.VoxelPos
import org.slf4j.Logger
import org.slf4j.LoggerFactory

fun ServerCommandDispatcher.registerIntentCommands(
    contents: NamespacedStorageAccess,
    intents: List<Intent> = contents.intents.values.toList(),
    handler: ServerHandler,
) = intents.forEach {
    val (rawId, name, script, inputs, actors, permission) = it
    val id = it.id.value.substringAfterLast('/')
    val node = literal(id)
        .requires { ctx -> permission == null || ctx.hasPermission(permission) }
    if (inputs.any { it.type is Input.Type.Table }) error("Can't create $id command intent: not supports table input type")

    fun ArgumentBuilder<CommandSourceStack, *>.executeIntent() = this.executeCatching { ctx ->
        val intent = contents.intents[rawId]!!
        val result = executeIntent(intent, ctx.getIntentScriptContext(inputs), contents, handler)
        if (result is ExecutionResult.Failure) {
            ctx.sendError(result.error)
        }
    }

    fun build(idx: Int): ArgumentBuilder<CommandSourceStack, *>? {
        val (inputId, inputType) = inputs.getOrNull(idx) ?: return null
        val argType = when (inputType) {
            Input.Type.Logic -> BoolArgumentType.bool()
            Input.Type.Double -> DoubleArgumentType.doubleArg()
            Input.Type.Integer -> IntegerArgumentType.integer()
            Input.Type.Table ->
                if (idx == inputs.lastIndex) StringArgumentType.greedyString()
                else StringArgumentType.string()

            is Input.Type.Text ->
                if (inputType.isSingleWord) StringArgumentType.word()
                else if (idx == inputs.lastIndex) StringArgumentType.greedyString()
                else StringArgumentType.string()
        }

        val next = argument(inputId, argType)

        val child = build(idx + 1)
        if (child != null) {
            next.then(child)
        } else {
            next.executeIntent()
        }

        return next
    }
    build(0)?.let { node.then(it) } ?: node.executeIntent()
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
            .map { input ->
                val argument = command.getArgument(input.id, input.type.kclass.java)
                (input as Input<Any>).valueOf(argument)
            },
        CommandIntentBehaviour(this, requireEntity())
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
    return CommandIntentBehaviour(null, table.client.getEntity(player.id)!!)
}

//TODO: сделать ClientCommandIntentBehaviour
class CommandIntentBehaviour(private val _context: Context?, private val entity: Entity) : IntentBehaviour {
    private val logger = LoggerFactory.getLogger(CommandIntentBehaviour::class.java)
    private val context
        get() = _context ?: run {
            logger.warn("Контекст выполнения команды не доступен в среде выполнения клиента")
            null
        }

    override fun generateTarget(): IntentTarget {
        val playerTable by injectEntityTable()

        return when(val result = raycastPlayerOrBlock(
            entity,
            SOCIAL_INTERACTION_DISTANCE.toDouble(),
            0f)
        ) {
            is BlockHitResult -> IntentTarget(null, result.blockPos.voxelPos(), result.location.engine())
            is EntityHitResult -> IntentTarget(
                playerTable.getGeneralPlayer(result.entity as Player),
                result.entity.blockPosition().voxelPos(),
                result.location.engine()
            )
            else -> error("Unexpected raycast hit result type $result")
        }
    }

    override fun generateSelection(): IntentSelection? {
        if (!isWorldEditAvailable()) friendlyError("World edit API is not available")
        val source = context?.source ?: friendlyError("World edit API is not available from client")
        val actor = FabricAdapter.adaptCommandSource(source)
        val session = WorldEdit.getInstance().sessionManager.get(actor)

        fun BlockVector3.engine() = VoxelPos(x(), y(), z())

        return runCatching { session.selection }.getOrNull()?.let {
            IntentSelection(it.minimumPoint.engine(), it.maximumPoint.engine())
        }
    }

    override fun feedback(string: String) {
        context?.sendFeedback(string, false)
    }
}

private fun raycastPlayerOrBlock(
    source: Entity,
    maxDistance: Double,
    tickDelta: Float
): HitResult = with(source.level()) {
    val start = source.getEyePosition(tickDelta)
    val direction = source.getViewVector(tickDelta)
    val end = start.add(direction.scale(maxDistance))

    val blockHit = clip(
        ClipContext(
            start,
            end,
            ClipContext.Block.OUTLINE,
            ClipContext.Fluid.NONE,
            source
        )
    )

    val searchBox = source.boundingBox
        .expandTowards(direction.scale(maxDistance))
        .inflate(1.0)

    val entityHit = ProjectileUtil.getEntityHitResult(
        this,
        source,
        start,
        end,
        searchBox,
        { entity -> entity is Player && entity != source },
        0f
    )

    if (entityHit != null) {
        val entityDist = start.distanceToSqr(entityHit.location)
        if (blockHit.type == HitResult.Type.MISS) {
            return entityHit
        }
        val blockDist = start.distanceToSqr(blockHit.location)
        if (entityDist < blockDist) {
            return entityHit
        }
    }

    return blockHit
}