package org.lain.engine.client.util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.lain.engine.Constants
import org.lain.engine.client.EngineMinecraftClient
import org.lain.engine.client.render.ui.ErrorReportToast
import org.lain.engine.client.resources.LOGGER
import org.lain.engine.server.account.ClientEnvironment
import org.lain.engine.server.account.ErrorReportPost
import org.lain.engine.server.account.errorReport

fun EngineMinecraftClient.postErrorReport(e: Exception, phase: String) {
    CoroutineScope(Dispatchers.IO).launch {
        val authorized = engine.accountManager.getAuthorized() ?: return@launch

        val toast = withClientContext {
            ErrorReportToast(client)
                .also { it.show() }
        }

        try {
            val report = e.errorReport(phase)
            val env = ClientEnvironment(
                Constants.ENGINE_MOD_VERSION,
                platform.modIds,
                !client.isSingleplayer
            )
            authorized.postErrorReport(ErrorReportPost(report, env))
        } catch (e: Throwable) {
            withClientContext { toast.failure() }
            LOGGER.error("Не удалось отправить отчёт об ошибке", e)
        }
        withClientContext { toast.success() }
    }
}