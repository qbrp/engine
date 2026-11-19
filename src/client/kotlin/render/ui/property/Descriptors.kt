package org.lain.engine.client.render.ui.property

import org.lain.cyberia.ecs.EntityId
import org.lain.engine.script.EngineId
import org.lain.engine.util.property.PropertyScalar
import org.lain.engine.util.property.PropertyTypeDescriptor
import org.lain.engine.util.property.PropertyValue
import org.lain.engine.util.property.ScalarKind

fun PropertyTypeDescriptor.emptyValue(): PropertyValue? = when(this) {
    is PropertyTypeDescriptor.Scalar -> when(kind) {
        ScalarKind.STRING -> PropertyValue.Primitive(PropertyScalar.Str(""))
        ScalarKind.CHAR -> PropertyValue.Primitive(PropertyScalar.Char(' '))
        ScalarKind.INT -> PropertyValue.Primitive(PropertyScalar.Int(0))
        ScalarKind.DOUBLE -> PropertyValue.Primitive(PropertyScalar.Double(0.0))
        ScalarKind.FLOAT -> PropertyValue.Primitive(PropertyScalar.Float(0f))
        ScalarKind.BOOL -> PropertyValue.Primitive(PropertyScalar.Bool(false))
        ScalarKind.ID -> PropertyValue.Primitive(PropertyScalar.Id(EngineId("namepsace/local")))
        ScalarKind.ENTITY_REF -> null
        ScalarKind.INSTANT -> null
        ScalarKind.UUID -> null
    }
    is PropertyTypeDescriptor.Collection -> TODO()
    is PropertyTypeDescriptor.Enum -> TODO()
    is PropertyTypeDescriptor.Map -> TODO()
    is PropertyTypeDescriptor.Nullable -> TODO()
    is PropertyTypeDescriptor.Record -> TODO()
    PropertyTypeDescriptor.Unknown -> TODO()
}
