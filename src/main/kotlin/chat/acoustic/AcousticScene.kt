package org.lain.engine.chat.acoustic

fun interface AcousticSceneView {
    fun getPassability(x: Int, y: Int, z: Int): Float
}
