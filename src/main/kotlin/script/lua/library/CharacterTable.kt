package org.lain.engine.script.lua.library

import org.lain.engine.player.character.BiologicalCategory
import org.lain.engine.player.character.BiologicalSex
import org.lain.engine.player.character.BodyType
import org.lain.engine.player.character.CharacterAppearance
import org.lain.engine.player.character.CharacterData
import org.lain.engine.player.character.CharacterGradientName
import org.lain.engine.player.character.CharacterHeight
import org.lain.engine.player.character.CharacterProfile
import org.lain.engine.player.character.Look
import org.lain.engine.player.character.Skin
import org.lain.engine.script.lua.luaNum
import org.lain.engine.script.lua.luaStr
import org.lain.engine.script.lua.luaTable
import org.lain.engine.script.lua.toLuaList
import org.luaj.vm2.LuaTable

fun BiologicalCategory.toLuaValue() = when (this) {
    BiologicalCategory.HUMAN -> "human"
    BiologicalCategory.HUMANLIKE_HUMANOID -> "humanlike"
    BiologicalCategory.HUMANOID -> "humanoid"
    BiologicalCategory.CREATURE -> "creature"
}.luaStr()

fun BiologicalSex.toLuaValue() = when (this) {
    BiologicalSex.MALE -> "male"
    BiologicalSex.FEMALE -> "female"
    BiologicalSex.OTHER -> "other"
}.luaStr()

fun BodyType.toLuaValue() = when (this) {
    BodyType.SLIM -> "slim"
    BodyType.NORMAL -> "normal"
    BodyType.BROAD -> "broad"
}.luaStr()

fun CharacterHeight.toLuaValue() = meters.toDouble().luaNum()

fun CharacterGradientName.toLuaValue() = luaTable {
    "text"(name)
    "color1"(firstColor.integer)
    "color2"((secondColor ?: firstColor).integer)
}

fun CharacterAppearance.toLuaValue(description: String) = luaTable {
    "breastSize"(breastSize)
    "eyeLevel"(eyeLevel)
    "description"(description)
}

fun CharacterProfile.toLuaValue() = luaTable {
    "name"(name.toLuaValue())
    "biology"(biologicalCategory.toLuaValue())
    "sex"(biologicalSex.toLuaValue())
    "height"(height.toLuaValue())
    "body_type"(bodyType.toLuaValue())
    "appearance"(appearance.toLuaValue(appearanceDescription))
}

fun Skin.toLuaValue() = luaTable {
    "url"(url)
}

fun Look.toLuaValue() = luaTable {
    "id"(id)
    "title"(title)
    "skin"(skin.toLuaValue())
    "description"(appearanceDescriptionAddon)
    "base"(base)
}

fun CharacterData.toLuaValue() = profile.toLuaValue().apply {
    set("looks", looks.toLuaList { it.toLuaValue() })
    set("base_look", baseLook.toLuaValue())
}

fun LuaTable.toLook() = Look(
    this["id"].tojstring(),
    this["title"].tojstring(),
    Skin(this["skin"].tojstring()),
    this["description"].tojstring(),
    this["base"].toboolean()
)