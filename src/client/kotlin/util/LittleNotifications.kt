package org.lain.engine.client.util

import org.lain.engine.client.render.EngineSprite
import org.lain.engine.client.render.WARNING
import org.lain.engine.server.Notification
import org.lain.engine.util.Color
import org.lain.engine.util.DEFAULT_TEXT_COLOR
import org.lain.engine.util.FREECAM_WARNING_COLOR
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

            else -> TODO()
        }
    }
}

const val SPECTATOR_NOTIFICATION = "spectator"