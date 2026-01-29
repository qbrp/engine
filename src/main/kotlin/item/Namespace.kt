package org.lain.engine.item

@JvmInline
value class ItemNamespaceId(val value: String) {
    init { require(!value.contains(" ")) { "Идентификатор содержит пробелы" } }

    override fun toString(): String {
        return value
    }
}

data class ItemNamespace(
    val id: ItemNamespaceId,
    val items: List<ItemId>
)