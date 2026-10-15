package org.lain.engine.script.dev

import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.full.primaryConstructor

fun KType.unwrapValueClassType(): KType {
    val valueClass = classifier as? KClass<*> ?: return this
    if (!valueClass.isValue) return this
    return valueClass.valueClassProperty().returnType.unwrapValueClassType()
}

fun Any?.wrapValueClasses(type: KType): Any? {
    if (this == null) return null
    val valueClass = type.classifier as? KClass<*> ?: return this
    if (!valueClass.isValue || valueClass.isInstance(this)) return this

    val constructor = valueClass.primaryConstructor
        ?: error("Value class ${valueClass.qualifiedName} has no primary constructor")
    val parameter = constructor.parameters.single()
    return constructor.call(this.wrapValueClasses(parameter.type))
}
