package org.lain.engine.client.util

import org.lain.engine.client.EngineClient
import org.lain.engine.client.render.EXCLAMATION_RED
import org.lain.engine.client.render.EngineSprite
import org.lain.engine.client.render.MAP
import org.lain.engine.client.render.QUESTION
import org.lain.engine.client.render.VOICE_WARNING
import org.lain.engine.client.render.WARNING
import org.lain.engine.server.Notification
import org.lain.engine.util.Color
import org.lain.engine.util.DEFAULT_TEXT_COLOR
import org.lain.engine.util.DEV_MODE_COLOR
import org.lain.engine.util.FREECAM_WARNING_COLOR
import org.lain.engine.util.INSPECTION_MODE_COLOR
import org.lain.engine.util.SPECTATOR_MODE_COLOR
import org.lain.engine.util.WARNING_COLOR

data class LittleNotification(
    val title: String,
    val description: String? = null,
    val color: Color = DEFAULT_TEXT_COLOR,
    val sprite: EngineSprite,
    val lifeTime: Long = 120,
    val transitionTime: Long = 12,
) {
    companion object {
        fun ofServer(type: Notification) : LittleNotification = when(type) {
            Notification.COMPILATION_ERROR -> {
                LittleNotification(
                    "Ошибка компиляции сервера",
                    "Проверьте консоль или логи для получения более подробной информации.",
                    WARNING_COLOR,
                    WARNING,
                    lifeTime = 240
                )
            }

            Notification.INVALID_SOURCE_POS ->
                LittleNotification(
                    "Выход за пределы мира",
                    "Сообщение в этой зоне обрабатываются некорректно — используется упрощенная симуляция.",
                    color = WARNING_COLOR,
                    sprite = WARNING,
                    lifeTime = 300
                )

            Notification.ACOUSTIC_ERROR -> {
                LittleNotification(
                    "Акустика сломалась",
                    "При обработке сообщения акустической системой возникла ошибка. Ваше сообщения не будет видно другим игрокам.",
                    color = WARNING_COLOR,
                    sprite = WARNING,
                    lifeTime = 240
                )
            }
            Notification.FREECAM ->
                LittleNotification(
                    "Вы используете мод Freecam",
                    "Его использование способствует получению мета-информации, для игры на сервере он запрещен.",
                    color = FREECAM_WARNING_COLOR,
                    sprite = WARNING,
                    lifeTime = 300
                )

            Notification.SPECTATOR_GAMEMODE ->
                LittleNotification(
                    "Режим наблюдателя",
                    color = Color.AQUA,
                    sprite = QUESTION,
                    lifeTime = 60
                )

            Notification.CREATIVE_GAMEMODE ->
                LittleNotification(
                    "Режим ГМа",
                    color = Color.AQUA,
                    sprite = QUESTION,
                    lifeTime = 60
                )

            Notification.SURVIVAL_GAMEMODE ->
                LittleNotification(
                    "Режим выживания",
                    color = Color.AQUA,
                    sprite = QUESTION,
                    lifeTime = 60
                )

            Notification.ADVENTURE_GAMEMODE ->
                LittleNotification(
                    "Режим приключений",
                    color = Color.AQUA,
                    sprite = QUESTION,
                    lifeTime = 60
                )

            Notification.CHANGE_GAMEMODE_FORBIDDEN ->
                LittleNotification(
                    "Нет персонажа",
                    "Для игры требуется выбрать персонажа. Если у вас нет персонажа, создайте его и перезайдите в мир.",
                    Color.RED,
                    EXCLAMATION_RED,
                    lifeTime = 160
                )
        }
    }
}

const val SPECTATOR_NOTIFICATION = "spectator"

fun EngineClient.showInpectionModeToggleNotification(value: Boolean) {
    val description = if (value) {
        "Подсказки будут автоматически отображаться при взгляде на блок"
    } else {
        "Выключен"
    }
    showNotification(
        LittleNotification(
            title = "Режим исследования",
            description = description,
            color = INSPECTION_MODE_COLOR,
            sprite = MAP
        )
    )
}

fun EngineClient.showCompilationErrorNotification(e: Exception) {
    showNotification(
        LittleNotification(
            "Ошибка компиляции клиента",
            "${e.message ?: "Неизвестная ошибка"}<newline>Проверьте консоль для более подробной информации.",
            WARNING_COLOR,
            WARNING,
            lifeTime = 240
        )
    )
}

fun EngineClient.showSpectatingNotification() {
    showNotification(
        LittleNotification(
            "Наблюдение",
            "Введите команду /spawn для появления",
            SPECTATOR_MODE_COLOR,
            sprite = QUESTION,
            lifeTime = 200
        ),
        SPECTATOR_NOTIFICATION
    )
}

fun EngineClient.showAcousticDebugNotification(enabled: Boolean) {
    val text = if (enabled) "Включена" else "Выключена"
    showNotification(
        LittleNotification(
            "Отладка акустики",
            text,
            DEV_MODE_COLOR,
            VOICE_WARNING
        )
    )
}