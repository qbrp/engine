package org.lain.engine.client.util

import dev.isxander.yacl3.api.Binding
import dev.isxander.yacl3.api.YetAnotherConfigLib
import dev.isxander.yacl3.api.controller.BooleanControllerBuilder
import dev.isxander.yacl3.api.controller.FloatSliderControllerBuilder
import dev.isxander.yacl3.api.controller.IntegerSliderControllerBuilder
import dev.isxander.yacl3.dsl.YetAnotherConfigLib
import net.minecraft.text.Text
import org.lain.engine.CommonEngineServerMod
import org.lain.engine.client.EngineClient
import org.lain.engine.client.mc.injectClient
import org.lain.engine.util.ENGINE_DIR
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Properties
import kotlin.reflect.full.memberProperties

private const val CONFIG_FILENAME = "client.properties"
private val CONFIG = ENGINE_DIR
    .resolve(CONFIG_FILENAME)

data class EngineOptions(
    val chatBubbleScale: EngineOption<Float> = EngineOption(1f),
    val chatBubbleHeight: EngineOption<Float> = EngineOption(2.3f),
    val chatBubbleLineWidth: EngineOption<Int>  = EngineOption(200),
    val chatBubbleLifeTime: EngineOption<Int> = EngineOption(300),
    val arcRadius: EngineOption<Float> = EngineOption(5f),
    val arcThickness: EngineOption<Float> = EngineOption(1.5f),
    val arcOffsetX: EngineOption<Float> = EngineOption(0f),
    val arcOffsetY: EngineOption<Float> = EngineOption(0f),
    val chatInputShakingForce: EngineOption<Float> = EngineOption(3f),
    val chatInputShakingThreshold: EngineOption<Float> = EngineOption(0.5f),
    val crosshairIndicatorVisible: EngineOption<Boolean> = EngineOption(false)
)

fun loadAndCreateEngineOptions() = EngineOptions().also { it.load() }

class EngineOption<T : Any>(initial: T) {
    private var ref = initial

    fun get(): T = ref

    fun set(value: T) {
        ref = value
    }

    fun save(key: String, props: Properties) {
        props[key] = get().toString()
    }

    fun load(key: String, props: Properties, parser: (String) -> T) {
        val value = props.getProperty(key)
        if (value != null) set(parser(value))
    }
}

fun EngineOptions.save(file: File = CONFIG) {
    val props = Properties()
    EngineOptions::class.memberProperties.forEach { prop ->
        val value = prop.get(this)
        if (value is EngineOption<*>) {
            value.save(prop.name, props)
        }
    }
    FileOutputStream(file).use { props.store(it, "EngineOptions") }
}

fun EngineOptions.load(file: File = CONFIG) {
    if (!file.exists()) {
        save(file)
    }
    val props = Properties()
    FileInputStream(file).use { props.load(it) }

    EngineOptions::class.memberProperties.forEach { prop ->
        val value = prop.get(this)
        if (value is EngineOption<*>) {
            @Suppress("UNCHECKED_CAST")
            val parser: (String) -> Any = when (value.get()) {
                is Int -> { s -> s.toInt() }
                is Float -> { s -> s.toFloat() }
                is Double -> { s -> s.toDouble() }
                is Boolean -> { s -> s.toBoolean() }
                else -> { s -> s }
            }
            (value as EngineOption<Any>).load(prop.name, props, parser)
        }
    }
}

data class EngineBinding<T : Any>(
    private val default: T,
    private val getter: (EngineOptions) -> EngineOption<T>,
    private val setter: EngineOptions.(T) -> Unit,
) : Binding<T> {
    private val client: EngineClient by injectClient()
    override fun setValue(value: T) {
        client.options.apply {
            setter(value)
            save()
        }
    }

    override fun getValue(): T? {
        return getter(client.options).get()
    }

    override fun defaultValue(): T? {
        return default
    }
}

fun setupOptionsConfig(): YetAnotherConfigLib {
    return YetAnotherConfigLib(CommonEngineServerMod.MOD_ID) {
        categories.register("chat") {
            name(Text.of("Чат"))
            groups.register("chat-bubbles") {
                name(Text.of("Сообщения над головами игроков"))
                options.register<Float>("chatBubbleScale") {
                    name(Text.of("Масштаб"))
                    binding(
                        EngineBinding<Float>(
                            1f,
                            { it.chatBubbleScale },
                            { value -> chatBubbleScale.set(value) },
                        )
                    )
                    controller {
                        FloatSliderControllerBuilder
                            .create(it)
                            .range(0.5f, 3f)
                            .step(0.05f)
                    }
                }
                options.register<Float>("chatBubbleHeight") {
                    name(Text.of("Высота"))
                    binding(
                        EngineBinding<Float>(
                            2.3f,
                            { it.chatBubbleHeight },
                            { value -> chatBubbleHeight.set(value) },
                        )
                    )
                    controller {
                        FloatSliderControllerBuilder
                            .create(it)
                            .range(1f, 10f)
                            .step(0.05f)
                    }
                }
                options.register<Int>("chatBubbleLineWidth") {
                    name(Text.of("Длина строки"))
                    binding(
                        EngineBinding<Int>(
                            200,
                            { it.chatBubbleLineWidth },
                            { value -> chatBubbleLineWidth.set(value) },
                        )
                    )
                    controller {
                        IntegerSliderControllerBuilder
                            .create(it)
                            .range(50, 1000)
                            .step(5)
                    }
                }
                options.register<Int>("chatBubbleLifeTime") {
                    name(Text.of("Время жизни"))
                    binding(
                        EngineBinding<Int>(
                            200,
                            { it.chatBubbleLifeTime },
                            { value -> chatBubbleLifeTime.set(value) },
                        )
                    )
                    controller {
                        IntegerSliderControllerBuilder
                            .create(it)
                            .range(2, 2400)
                            .formatValue { value -> Text.of { "$value тиков (${"%.1f".format(value / 20f)} секунд)" } }
                            .step(2)
                    }
                }
            }
            groups.register("chat-shake") {
                name(Text.of("Тряска"))
                options.register<Float>("chatInputShakingForce") {
                    name(Text.of("Сила"))
                    binding(
                        EngineBinding(
                            3f,
                            { it.chatInputShakingForce },
                            { value -> chatInputShakingForce.set(value) },
                        )
                    )
                    controller {
                        FloatSliderControllerBuilder
                            .create(it)
                            .range(0f, 5f)
                            .step(0.05f)
                    }
                }
                options.register<Float>("chatInputShakingThreshold") {
                    name(Text.of("Порог"))
                    binding(
                        EngineBinding(
                            0.5f,
                            { it.chatInputShakingThreshold },
                            { value -> chatInputShakingThreshold.set(value) },
                        )
                    )
                    controller {
                        FloatSliderControllerBuilder
                            .create(it)
                            .range(0f, 1f)
                            .step(0.01f)
                    }
                }
            }
        }
        categories.register("cursor") {
            name(Text.of("Курсор"))
            rootOptions.register<Boolean>("crosshair") {
                name(Text.of("Показывать ванильный индникатор атаки"))
                binding(
                    EngineBinding(
                        false,
                        { it.crosshairIndicatorVisible },
                        { value -> crosshairIndicatorVisible.set(value) },
                    )
                )
                controller {
                    BooleanControllerBuilder.create(it)
                }
            }
            groups.register("arc") {
                name(Text.of("Колесо скорости"))
                options.register<Float>("arcRadius") {
                    name(Text.of("Радиус"))
                    binding(
                        EngineBinding(
                            5f,
                            { it.arcRadius },
                            { value -> arcRadius.set(value) },
                        )
                    )
                    controller {
                        FloatSliderControllerBuilder
                            .create(it)
                            .range(0.05f, 10f)
                            .formatValue { value -> Text.of("%.2f".format(value)) }
                            .step(0.05f)
                    }
                }
                options.register<Float>("arcThickness") {
                    name(Text.of("Толщина"))
                    binding(
                        EngineBinding<Float>(
                            1.5f,
                            { it.arcThickness },
                            { value -> arcThickness.set(value) },
                        )
                    )
                    controller {
                        FloatSliderControllerBuilder
                            .create(it)
                            .range(0.05f, 10f)
                            .step(0.05f)
                    }
                }
                options.register("arcOffsetX") {
                    name(Text.of("Смещение по X"))
                    binding(
                        EngineBinding<Float>(
                            0f,
                            { it.arcOffsetX },
                            { value -> arcOffsetX.set(value) },
                        )
                    )
                    controller {
                        FloatSliderControllerBuilder
                            .create(it)
                            .range(-10f, 10f)
                            .step(0.05f)
                    }
                }
                options.register("arcOffsetY") {
                    name(Text.of("Смещение по Y"))
                    binding(
                        EngineBinding<Float>(
                            0f,
                            { it.arcOffsetY },
                            { value -> arcOffsetY.set(value) },
                        )
                    )
                    controller {
                        FloatSliderControllerBuilder
                            .create(it)
                            .range(-10f, 10f)
                            .step(0.05f)
                    }
                }
            }
        }
    }
}

