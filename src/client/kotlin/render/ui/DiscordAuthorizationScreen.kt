package org.lain.engine.client.render.ui

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import net.minecraft.client.gui.screens.Screen
import org.lain.engine.client.EngineClient
import org.lain.engine.mc.literalText
import java.util.concurrent.CompletableFuture

class DiscordAuthorizationScreen(private val client: EngineClient) : Screen(literalText("Engine account")) {
    private var job: Job? = null

    override fun init() {
        val widget = widgetWithMargins(4, webPageUrl("login"))
        addRenderableWidget(widget)
        widget.bridge().onEvent("login") { _, _ ->
            CoroutineScope(Dispatchers.IO).launch {
                job = client.accountManager.authorizeDiscordOAuth2()
                job?.join()
                minecraft.execute { onClose() }
            }
        }

        widget.bridge().onEventJson("auto_login_checkbox", Boolean::class.java) { _, bool ->
            client.options.autoLogin = bool
        }

        widget.bridge().onRequest("auto_login_checkbox_value") { _, _ ->
            CompletableFuture.completedFuture(client.options.autoLogin.toString())
        }
    }

    override fun onClose() {
        super.onClose()
        job?.cancel()
    }
}