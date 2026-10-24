package org.lain.engine.client.resources

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.future.asCompletableFuture
import org.lain.engine.script.ModuleManager

class AssetCompilation(
    private val moduleManager: ModuleManager,
    val scope: CoroutineScope
) {
    val indexedAssets = scope.async { moduleManager.composeModules().compileAssets() }
    val modelCatalog = scope.async { indexedAssets.await().catalogizeModels() }
    val modelCatalogFuture = modelCatalog.asCompletableFuture()
}