package org.lain.engine.client.resources

import org.lain.engine.script.EngineId

data class Assets(
    val textureAssets: Map<String, Map<EngineId, Asset>> = mapOf(),
    val propModels: Map<EngineId, Asset> = mapOf(),
    val objModels: Map<EngineId, Asset> = mapOf(),
    val mtlFiles: Map<EngineId, Asset> = mapOf(),
    val sounds: Map<EngineId, Asset> = mapOf(),
    val glbBundles: Map<EngineId, Asset> = mapOf(),
)