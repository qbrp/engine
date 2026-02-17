package org.lain.engine.client.util

import org.lain.engine.server.ServerId
import org.lain.engine.world.ChunkStorage

data class SavedState(val serverId: ServerId, val chunkStorage: ChunkStorage)