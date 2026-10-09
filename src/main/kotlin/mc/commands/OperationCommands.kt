package org.lain.engine.mc.commands

import com.mojang.brigadier.arguments.BoolArgumentType
import com.mojang.brigadier.arguments.DoubleArgumentType
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.ArgumentBuilder
import com.mojang.brigadier.builder.RequiredArgumentBuilder
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
import org.lain.engine.Constants
import org.lain.engine.mc.ecs.minecraftEntity
import org.lain.engine.mc.engine
import org.lain.engine.mc.getEngineState
import org.lain.engine.mc.requireEngineState
import org.lain.engine.mc.voxelPos
import org.lain.engine.player.EnginePlayer
import org.lain.engine.player.interaction.SOCIAL_INTERACTION_DISTANCE
import org.lain.engine.script.ExecutionResult
import org.lain.engine.script.Input
import org.lain.engine.script.InputType
import org.lain.engine.script.InputValue
import org.lain.engine.script.Operation
import org.lain.engine.script.OperationActor
import org.lain.engine.script.OperationBehaviour
import org.lain.engine.script.OperationSelection
import org.lain.engine.script.OperationTarget
import org.lain.engine.script.SBool
import org.lain.engine.script.SInt
import org.lain.engine.script.SList
import org.lain.engine.script.SNumber
import org.lain.engine.script.SString
import org.lain.engine.script.STable
import org.lain.engine.script.ScriptValue
import org.lain.engine.script.ScriptContext
import org.lain.engine.script.SelectionEntry
import org.lain.engine.script.execute
import org.lain.engine.script.parseScriptValue
import org.lain.engine.script.resolve
import org.lain.engine.server.ServerHandler
import org.lain.engine.world.VoxelPos
import org.slf4j.LoggerFactory

private val operationCommandLogger = LoggerFactory.getLogger("Operation Commands")

fun ServerCommandDispatcher.registerOperationCommands(
    operations: Collection<Operation>,
    handler: ServerHandler,
) = operations.forEach { operation ->
    val id = operation.id.value.local
    val inputs = operation.inputs.commandOrder()
    val node = literal(id)
        .requires { ctx -> operation.permission == null || ctx.hasPermission(operation.permission) }

    fun ArgumentBuilder<CommandSourceStack, *>.executeOperation() = this.executeCatching { ctx ->
        val result = operation.execute(ctx.getOperationScriptContext(operation, inputs), handler)
        if (result is ExecutionResult.Failure) {
            ctx.sendError(result.error)
        }
    }

    fun build(idx: Int): ArgumentBuilder<CommandSourceStack, *>? {
        val (inputId, inputType) = inputs.getOrNull(idx) ?: return null
        val next: RequiredArgumentBuilder<CommandSourceStack, *> = when (inputType) {
            InputType.Logic -> argument(inputId, BoolArgumentType.bool())
            is InputType.Double -> argument(
                inputId,
                DoubleArgumentType.doubleArg(
                    inputType.min ?: -Double.MAX_VALUE,
                    inputType.max ?: Double.MAX_VALUE,
                )
            )
            is InputType.Integer -> argument(
                inputId,
                IntegerArgumentType.integer(
                    inputType.min ?: Int.MIN_VALUE,
                    inputType.max ?: Int.MAX_VALUE,
                )
            )
            InputType.Table -> argument(inputId, StringArgumentType.greedyString())
            is InputType.Text -> argument(
                inputId,
                if (inputType.isSingleWord) StringArgumentType.word() else StringArgumentType.greedyString()
            )
            is InputType.Selection -> argument(inputId, StringArgumentType.string())
                .suggests { command, builder ->
                    val entries = command.source.player?.getEngineState()?.let { player ->
                        val actor = player.commandActor()
                        runCatching {
                            val inputValues = inputs.take(idx).readValues(command, actor, operation)
                            inputType.resolveVariants(
                                ScriptContext.OperationInputResolution(
                                    actor,
                                    operation.id,
                                    inputId,
                                    inputValues,
                                )
                            )
                        }.onFailure { error ->
                            operationCommandLogger.warn(
                                "Failed to resolve variants for operation input ${operation.id}/$inputId",
                                error,
                            )
                        }.getOrDefault(emptyList())
                    } ?: emptyList()

                    entries
                        .map(SelectionEntry::id)
                        .filter { it.startsWith(builder.remaining, ignoreCase = true) }
                        .forEach { builder.suggest(StringArgumentType.escapeIfRequired(it)) }
                    builder.buildFuture()
                }
        }

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

fun Context.getOperationScriptContext(
    operation: Operation,
    inputs: List<Input>,
): ScriptContext.OperationExecution {
    val actor = requirePlayer().commandActor()
    return ScriptContext.OperationExecution(
        actor,
        null,
        inputs.readValues(command, actor, operation),
        CommandOperationBehaviour(this, requireEntity())
    )
}

private fun EnginePlayer.commandActor() = OperationActor(
    OperationActor.Type.COMMAND,
    this,
    entity,
)

private fun List<Input>.readValues(
    context: ServerCommandContext,
    actor: OperationActor,
    operation: Operation,
): List<InputValue> = buildList(size) {
    for (input in this@readValues) {
        val resolutionContext = ScriptContext.OperationInputResolution(
            actor,
            operation.id,
            input.id,
            this,
        )
        add(input.valueOf(input.readValue(context, resolutionContext)))
    }
}

private fun Input.readValue(
    context: ServerCommandContext,
    resolutionContext: ScriptContext.OperationInputResolution,
): ScriptValue = when (val type = type) {
    InputType.Logic -> SBool(BoolArgumentType.getBool(context, id))
    is InputType.Integer -> SInt(IntegerArgumentType.getInteger(context, id))
    is InputType.Double -> SNumber(DoubleArgumentType.getDouble(context, id))
    is InputType.Text -> SString(StringArgumentType.getString(context, id))
    InputType.Table -> StringArgumentType.getString(context, id)
        .parseScriptValue()
        .also { require(it is STable || it is SList) { "Argument '$id' must be a Lua table" } }
    is InputType.Selection -> {
        val token = StringArgumentType.getString(context, id)
        type.resolveVariants(resolutionContext)
            .firstOrNull { it.id == token }
            ?.value
            ?: error("Неизвестное значение '$token' для параметра '$id'")
    }
}

private fun InputType.Selection.resolveVariants(
    context: ScriptContext.OperationInputResolution,
): List<SelectionEntry> = when (val result = variants.resolve(context)) {
    is ExecutionResult.Success -> result.value
    is ExecutionResult.Failure -> throw result.error
}

private fun List<Input>.commandOrder(): List<Input> {
    val greedy = filter { input ->
        input.type == InputType.Table || input.type is InputType.Text && !input.type.isSingleWord
    }
    require(greedy.size <= 1) {
        "An operation command can contain only one table or multi-word text input"
    }
    return filterNot { it in greedy } + greedy
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
        if (!Constants.WORLD_EDIT_AVAILABLE) friendlyError("World edit API is not available")
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
