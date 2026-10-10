package org.lain.engine.script.dev

import org.lain.cyberia.ecs.Component
import org.lain.cyberia.ecs.ComponentType
import org.lain.cyberia.ecs.setComponent
import org.lain.engine.script.SString
import org.lain.engine.world.World
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.KParameter
import kotlin.reflect.KProperty1
import kotlin.reflect.KType
import kotlin.reflect.KClass
import kotlin.reflect.full.instanceParameter
import kotlin.reflect.full.memberFunctions
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor

context(world: World)
fun EntityInspection.applyEdit(objectId: Int, property: String, value: InspectionPrimitive) {
    val inspection = lastSnapshot.debugObjects[objectId]!!
    val inspected = lastSnapshot.gameObjects[objectId]!!
    when (val live = inspected.value) {
        is ScriptInspectionValue.List -> live.set(property.toInt(), value.toScriptValue())
        is ScriptInspectionValue.Table -> live.set(SString(property), value.toScriptValue())
        else -> {
            inspected.tryMutateProperty(inspection, property, value.toJvmValue())
        }
    }
}

context(inspection: EntityInspection, world: World)
fun InspectedObject.tryMutateProperty(
    inspectionObject: InspectionObject,
    property: String,
    newValue: Any
) {
    val inspectionProperty = inspectionObject.getValue(property)!!
    require(inspectionProperty is InspectionValue.Primitive)

    when(value) {
        is Map<*, *> -> {

        }
        else -> {
            val liveProperty = value::class.memberProperties
                .find { it.name == property } as? KProperty1<Any, *>
                ?: error("Property $property not found on ${value::class.qualifiedName}")

            val adaptedValue = newValue.adaptTo(liveProperty.returnType)

            if (!inspectionProperty.readonly) {
                val mutableProperty = liveProperty as? KMutableProperty1<Any, Any?>
                    ?: error("Mutable property $property not found on ${value::class.qualifiedName}")
                mutableProperty.set(value, adaptedValue)
            } else {
                inspection.replaceAtParent(
                    parent,
                    value.copyWith(property, adaptedValue)
                )
            }
        }
    }
}

private fun Any?.adaptTo(type: KType): Any? {
    if (this == null) return null
    val valueClass = type.classifier as? KClass<*> ?: return this
    if (!valueClass.isValue || valueClass.isInstance(this)) return this

    val constructor = valueClass.primaryConstructor
        ?: error("Value class ${valueClass.qualifiedName} has no primary constructor")
    val parameter = constructor.parameters.single()
    return constructor.call(adaptTo(parameter.type))
}

context(world: World)
private fun EntityInspection.replaceAtParent(
    parent: InspectedObject.Parent,
    newValue: Any,
) {
    when (parent) {
        is InspectedObject.Parent.Component -> {
            @Suppress("UNCHECKED_CAST")
            entity.setComponent(
                newValue as Component,
                parent.type as ComponentType<Component>
            )
        }

        is InspectedObject.Parent.Property -> {
            val parentObject = lastSnapshot.gameObjects[parent.objectId]
                ?: error("Parent object ${parent.objectId} not found")
            val parentInspection = lastSnapshot.debugObjects[parent.objectId]
                ?: error("Parent inspection object ${parent.objectId} not found")

            parentObject.tryMutateProperty(
                parentInspection,
                parent.name,
                newValue
            )
        }

        is InspectedObject.Parent.Index -> {
            val collectionObject = lastSnapshot.gameObjects[parent.objectId]
                ?: error("Collection ${parent.objectId} not found")
            val list = collectionObject.value as? List<*>
                ?: error("${collectionObject.value::class.qualifiedName} is not a list")

            require(parent.index in list.indices) {
                "Index ${parent.index} is outside list bounds"
            }

            val listCopy = list.toMutableList()
            listCopy[parent.index] = newValue

            replaceAtParent(collectionObject.parent, listCopy)
        }
    }
}

fun Any.copyWith(property: String, value: Any?): Any {
    require(this::class.isData) {
        "${this::class.qualifiedName} is not a data class"
    }

    val copyFunction = this::class.memberFunctions
        .single { it.name == "copy" }

    val propertyParameter = copyFunction.parameters
        .singleOrNull {
            it.kind == KParameter.Kind.VALUE && it.name == property
        }
        ?: error("Copy parameter $property not found")

    return copyFunction.callBy(
        mapOf(
            copyFunction.instanceParameter!! to this,
            propertyParameter to value
        )
    ) ?: error("copy returned null")
}
