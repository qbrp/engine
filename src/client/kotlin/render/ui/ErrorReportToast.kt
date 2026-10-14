package org.lain.engine.client.render.ui

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.components.toasts.SystemToast
import org.lain.engine.mc.literalText

class ErrorReportToast(private val minecraft: Minecraft) {
    private val id = SystemToast.SystemToastId(Long.MAX_VALUE)

    fun show() {
        minecraft.toasts.addToast(
            SystemToast(
                id,
                literalText("Отправка отчёта об ошибке..."),
                literalText("")
            )
        )
    }

    fun success() {
        SystemToast.forceHide(minecraft.toasts, id)

        minecraft.toasts.addToast(
            SystemToast(
                SystemToast.SystemToastId(),
                literalText("Отчёт отправлен"),
                literalText("")
            )
        )
    }

    fun failure() {
        SystemToast.forceHide(minecraft.toasts, id)
    }
}