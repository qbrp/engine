package org.lain.engine.server

import kotlinx.serialization.Serializable

@Serializable
@JvmInline
value class ServerId(val value: String)