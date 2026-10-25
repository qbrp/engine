package org.lain.engine.client.render

import org.lain.engine.client.EngineClient
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
    val lifeTime: Long? = 120,
    val transitionTime: Long = 12,
)

const val SPECTATOR_NOTIFICATION = "spectator"

fun EngineClient.showServerNotification(type: Notification) {
    when (type) {
        Notification.COMPILATION_ERROR -> {
            showNotification(
                LittleNotification(
                    "Ошибка компиляции сервера",
                    "Проверьте консоль или логи для получения более подробной информации.",
                    WARNING_COLOR,
                    WARNING,
                    lifeTime = 240
                )
            )
        }

        Notification.INVALID_SOURCE_POS ->
            showNotification(
                LittleNotification(
                    "Выход за пределы мира",
                    "Сообщение в этой зоне обрабатываются некорректно — используется упрощенная симуляция.",
                    color = WARNING_COLOR,
                    sprite = WARNING,
                    lifeTime = 300
                )
            )

        Notification.ACOUSTIC_ERROR -> {
            showNotification(
                LittleNotification(
                    "Акустика сломалась",
                    "При обработке сообщения акустической системой возникла ошибка. Ваше сообщения не будет видно другим игрокам.",
                    color = WARNING_COLOR,
                    sprite = WARNING,
                    lifeTime = 240
                )
            )
        }


        Notification.FREECAM -> {
            showNotification(
                LittleNotification(
                    "Вы используете мод Freecam",
                    "Его использование способствует получению мета-информации, для игры на сервере он запрещен.",
                    color = FREECAM_WARNING_COLOR,
                    sprite = WARNING,
                    lifeTime = 300
                )
            )
        }

        Notification.SPECTATOR_GAMEMODE -> {
            showNotification(
                LittleNotification(
                    "Режим наблюдателя",
                    color = Color.AQUA,
                    sprite = QUESTION,
                    lifeTime = 60
                ),
                "gamemode"
            )
        }

        Notification.CREATIVE_GAMEMODE -> {
            showNotification(
                LittleNotification(
                    "Режим ГМа",
                    color = Color.AQUA,
                    sprite = QUESTION,
                    lifeTime = 60
                ),
                "gamemode"
            )
        }

        Notification.SURVIVAL_GAMEMODE -> {
            showNotification(
                LittleNotification(
                    "Режим выживания",
                    color = Color.AQUA,
                    sprite = QUESTION,
                    lifeTime = 60
                ),
                "gamemode"
            )
        }

        Notification.ADVENTURE_GAMEMODE ->
            showNotification(
                LittleNotification(
                    "Режим приключений",
                    color = Color.AQUA,
                    sprite = QUESTION,
                    lifeTime = 60
                ),
                "gamemode"
            )

        Notification.CHANGE_GAMEMODE_FORBIDDEN ->
            showNotification(
                LittleNotification(
                    "Нет персонажа",
                    "Для игры требуется выбрать персонажа. Если у вас нет персонажа, создайте его и перезайдите в мир.",
                    Color.RED,
                    EXCLAMATION_RED,
                    lifeTime = 160
                )
            )

        Notification.BLOCKHINT_PERMISSION ->
            showNotification(
                LittleNotification(
                    "Недостаточно прав",
                    "",
                    Color.RED,
                    WARNING,
                    lifeTime = 160
                )
            )
    }
}

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
        ),
        "inspection"
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
        ),
        "acoustic_debug"
    )
}