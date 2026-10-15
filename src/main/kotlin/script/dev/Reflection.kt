package org.lain.engine.script.dev

import kotlin.reflect.KClass
import kotlin.reflect.KProperty1
import kotlin.reflect.KType
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor

val KType.propertyClassifier: KClass<*>
    get() = classifier as? KClass<*>
        ?: error("Type classifier is not KClass: $this")

fun KType.collectionTypeArgumentOrNull(): KType? {
    val collectionClass = classifier as? KClass<*> ?: return null
    if (!collectionClass.isSubclassOf(Collection::class)) return null
    return arguments.singleOrNull()?.type
}

fun KType.mapTypeArgumentsOrNull(): Pair<KType?, KType?>? {
    val mapClass = classifier as? KClass<*> ?: return null
    if (!mapClass.isSubclassOf(Map::class)) return null
    return arguments.getOrNull(0)?.type to arguments.getOrNull(1)?.type
}

@Suppress("UNCHECKED_CAST")
fun KClass<*>.constructorProperties(): List<KProperty1<Any, *>> {
    val constructorPropertyNames = primaryConstructor
        ?.parameters
        ?.mapNotNull { it.name }
        ?.toSet()
        .orEmpty()

    return memberProperties
        .filter { it.name in constructorPropertyNames } as List<KProperty1<Any, *>>
}

fun KClass<*>.valueClassProperty(): KProperty1<Any, *> {
    require(isValue) { "$this is not a value class" }
    return constructorProperties().singleOrNull()
        ?: error("Value class $this must have exactly one constructor property")
}
