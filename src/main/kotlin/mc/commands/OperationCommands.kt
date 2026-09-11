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
import org.lain.engine.mc.ecs.minecraftEntity
import org.lain.engine.mc.engine
import org.lain.engine.mc.requireEngineState
import org.lain.engine.mc.voxelPos
import org.lain.engine.player.EnginePlayer
import org.lain.engine.player.interaction.SOCIAL_INTERACTION_DISTANCE
import org.lain.engine.script.ExecutionResult
import org.lain.engine.script.OperationBehaviour
import org.lain.engine.script.NamespacedStorageAccess
import org.lain.engine.script.ScriptContext
import org.lain.engine.server.ServerHandler
import org.lain.engine.util.*
import org.lain.engine.world.VoxelPos
import org.slf4j.LoggerFactory

fun ServerCommandDispatcher.registerOperationCommands(
    operations: Collection<Operation>,
    handler: ServerHandler,
) = operations.forEach { operation ->
    val (rawId, name, script, inputs, actors, permission) = operation
    val id = operation.id.value.namespace.substringAfterLast('/')
    val node = literal(id)
        .requires { ctx -> permission == null || ctx.hasPermission(permission) }
    if (inputs.any { it.type is Input.Type.Table }) error("Can't create $id command operation: table input type is not supported")

    fun ArgumentBuilder<CommandSourceStack, *>.executeOperation() = this.executeCatching { ctx ->
        val result = operation.execute(ctx.getOperationScriptContext(inputs), handler)
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
            next.executeOperation()
        }

        return next
    }
    build(0)?.let { node.then(it) } ?: node.executeOperation()
    register(node)
}

fun Context.getOperationScriptContext(inputs: List<AnyInput>): ScriptContext.OperationExecution {
    val enginePlayer = requirePlayer()
    return ScriptContext.OperationExecution(
        OperationActor(
            OperationActor.Type.COMMAND,
            enginePlayer,
            enginePlayer.entity,
        ),
        null,
        inputs
            .filter { input -> command.nodes.find { it.node.name == input.id } != null }
            .map { input ->
                val argument = command.getArgument(input.id, input.type.kclass.java)
                (input as Input<Any>).valueOf(argument)
            },
        CommandOperationBehaviour(this, requireEntity())
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

fun ClientCommandOperationBehaviour(player: EnginePlayer): CommandOperationBehaviour {
    return CommandOperationBehaviour(null, player.minecraftEntity)
}

//TODO: сделать ClientCommandOperationBehaviour
class CommandOperationBehaviour(private val _context: Context?, private val entity: Entity) :
    OperationBehaviour {
    private val logger = LoggerFactory.getLogger(CommandOperationBehaviour::class.java)
    private val context
        get() = _context ?: run {
            logger.warn("Контекст выполнения команды не доступен в среде выполнения клиента")
            null
        }

    override fun generateTarget(): OperationTarget {
        return when (val result = raycastPlayerOrBlock(
            entity,
            SOCIAL_INTERACTION_DISTANCE.toDouble(),
            0f
        )
        ) {
            is BlockHitResult -> OperationTarget(
                null,
                result.blockPos.voxelPos(),
                result.location.engine()
            )

            is EntityHitResult -> OperationTarget(
                (result.entity as Player).requireEngineState(),
                result.entity.blockPosition().voxelPos(),
                result.location.engine()
            )

            else -> error("Unexpected raycast hit result type $result")
        }
    }

    override fun generateSelection(): OperationSelection? {
        if (WORLD_EDIT_AVAILABLE) friendlyError("World edit API is not available")
        val source = context?.source ?: friendlyError("World edit API is not available from client")
        val actor = FabricAdapter.adaptCommandSource(source)
        val session = WorldEdit.getInstance().sessionManager.get(actor)

        fun BlockVector3.engine() = VoxelPos(x(), y(), z())

        return runCatching { session.selection }.getOrNull()?.let {
            OperationSelection(it.minimumPoint.engine(), it.maximumPoint.engine())
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
