package org.lain.engine.script.dev

import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.full.isSubclassOf

fun KType.listTypeArgument(): KType {
    val listClass = classifier as? KClass<*> ?: error("Classifier not found")
    if (!listClass.isSubclassOf(List::class)) {
        error("Type is not list")
    }
    return arguments.singleOrNull()?.type ?: error("List type does not have element type argument")
}

fun KType.mapTypeArguments(): Pair<KType, KType> {
    val listClass = classifier as? KClass<*> ?: error("Classifier not found")
    if (!listClass.isSubclassOf(Map::class)) {
        error("Type is not map")
    }
    val keyArgument = arguments[0].type ?: error("Map does not have key type")
    val valueArgument = arguments[1].type ?: error("Map does not have value type")
    return keyArgument to valueArgument
}