package org.lain.engine.test

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.lain.engine.script.EngineId
import org.lain.engine.script.EngineIdentifierException
import org.lain.engine.script.Module
import org.lain.engine.script.NamespaceId
import org.lain.engine.script.lua.library.IdLibrary
import org.lain.engine.script.lua.library.resolveIdReference
import org.lain.engine.script.resolveId
import org.luaj.vm2.LuaValue

class IdTest {
    @Test
    fun testEngineIdValidation() {
        val withoutNamespace = EngineId("id_without_namespace")
        assert(withoutNamespace.namespace == EngineId.UNDEFINED_NAMESPACE)
        assert(withoutNamespace.local == "id_without_namespace")
        assert(
            withoutNamespace.full == "${EngineId.UNDEFINED_NAMESPACE}/id_without_namespace"
        )
        assertThrows<EngineIdentifierException> { EngineId("test/invalid!") }
    }

    @Test
    fun moduleResolvesLocalIdsAgainstItsPrimaryNamespace() {
        val primaryNamespace = NamespaceId("bodies")
        val additionalNamespace = NamespaceId("shared")
        val module = Module(
            namespace = primaryNamespace,
            namespaces = listOf(additionalNamespace, primaryNamespace),
        )

        assertEquals(
            listOf(additionalNamespace, primaryNamespace),
            module.allNamespaces,
        )
        assertEquals(
            EngineId.of(primaryNamespace, "body"),
            assertDoesNotThrow { module.resolveId("body") },
        )
    }

    @Test
    fun idLibraryReturnsEngineIdsAndNilOnParseFailure() {
        val library = IdLibrary().library
        val parse = library.get("parse")

        val successful = parse.invoke(LuaValue.valueOf("bodies/body"))
        val id = successful.arg1()
        assertEquals("bodies", id.get("namespace").checkjstring())
        assertEquals("body", id.get("loc").checkjstring())
        assertEquals(EngineId("bodies/body"), id.resolveIdReference())
        assertTrue(successful.arg(2).isnil())

        val failed = parse.invoke(LuaValue.valueOf("bodies/INVALID"))
        assertTrue(failed.arg1().isnil())
        assertTrue(failed.arg(2).isstring())
    }
}
