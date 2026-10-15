package org.lain.engine.client.util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.lain.engine.Constants
import org.lain.engine.client.EngineMinecraftClient
import org.lain.engine.client.resources.LOGGER
import org.lain.engine.server.account.ClientEnvironment
import org.lain.engine.server.account.ErrorReportPost
import org.lain.engine.server.account.errorReport

fun EngineMinecraftClient.postErrorReport(e: Exception, phase: String) {
    val report = e.errorReport(phase)
    CoroutineScope(Dispatchers.IO).launch {
        val authorized = engine.accountManager.getAuthorized() ?: return@launch
        try {
            val env = ClientEnvironment(
                Constants.ENGINE_MOD_VERSION,
                platform.modIds,
                !client.isSingleplayer
            )
            authorized.postErrorReport(ErrorReportPost(report, env))
        } catch (e: Throwable) {
            LOGGER.error("Не удалось отправить отчёт об ошибке", e)
        }
    }
}