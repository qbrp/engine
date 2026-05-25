//package org.lain.engine.test
//
//import org.lain.engine.script.NamespacedStorageAccess
//import org.lain.engine.script.ThreadSafeNamespaceStorageAccessImpl
//import org.lain.engine.script.emptyNamespacedStorage
//import org.lain.engine.script.lua.LuaContext
//import org.lain.engine.script.lua.LuaDataStorage
//import org.lain.engine.script.lua.LuaDependencies
//import org.lain.engine.script.lua.ResourceScriptSource
//import org.luaj.vm2.lib.jse.JsePlatform
//import kotlin.test.BeforeTest
//import kotlin.test.Test
//
//class ScriptTest : EngineTest() {
//    private lateinit var namespacedStorage: NamespacedStorageAccess
//    private lateinit var luaContext: LuaContext
//
//    @BeforeTest
//    fun setup() {
//        namespacedStorage = ThreadSafeNamespaceStorageAccessImpl(emptyNamespacedStorage())
//    }
//
//    fun setupLua(entrypointResource: String) {
//        luaContext = LuaContext(
//            LuaDependencies(
//                JsePlatform.standardGlobals(),
//                namespacedStorage,
//                "none",
//                LuaDataStorage()
//            ),
//            ResourceScriptSource(entrypointResource)
//        )
//    }
//
//    @Test
//    fun testComponentOperations() {
//        setupLua("component-operations.lua")
//        luaContext.setup(ResourceScriptSource("boot.lua"))
//        luaContext.runEntrypoint()
//    }
//}
