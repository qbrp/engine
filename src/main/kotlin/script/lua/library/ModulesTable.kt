package org.lain.engine.script.lua.library

import org.lain.engine.script.Module
import org.lain.engine.script.ModuleLocation
import org.lain.engine.script.ModuleManager
import org.lain.engine.script.lua.*
import org.lain.engine.script.resolveId
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaUserdata
import org.luaj.vm2.LuaValue
import java.io.File

data class LuaFileModule(
    val module: Module,
    val file: ModuleLocation,
    val document: LuaUserdata,
    val directory: LuaUserdata,
    val namespacesL: LuaTable = module.allNamespaces.toLuaList { it.value.luaStr() },
    val allowedNamespacesL: LuaTable = module.allNamespaces.associateWith { luaValue(true) }
        .toLuaTable(
            keyTransform = { it.value.luaStr() },
            valueTransform = { it }
        ),
    val dependsL: LuaTable = module.depends.toLuaList { it.module.value.luaStr() }
)

data class LuaModuleFile(val path: File)

data class LuaModuleFolder(val file: File)

context(lua: LuaScriptEngine)
fun File.resolveModuleEntry(): LuaValue {
    return if (isDirectory) {
        lua.moduleFolderMetaTable.newInstance(LuaModuleFolder(this))
    } else {
        lua.moduleFileMetaTable.newInstance(LuaModuleFile(this))
    }
}

context(lua: LuaScriptEngine)
fun ModuleFileUserdataType() = LuaUserdataType<LuaModuleFile> {
    functionSelf("read") { self ->
        self.path.readText().luaStr()
    }
    indexSelf { self, key ->
        when(key.tojstring()) {
            "path" -> self.path.path.luaStr()
            "name" -> self.path.name.luaStr()
            "kind" -> luaValue("file")
            else -> NIL
        }
    }
}

context(lua: LuaScriptEngine)
fun ModuleFolderUserdataType() = LuaUserdataType<LuaModuleFolder>() {
    functionSelf("files") { self ->
        val files = self.file.listFiles()
            .toList()
            .associateBy { it.path }
        files.toLuaTable(
            { it.luaStr() },
            { it.resolveModuleEntry() }
        )
    }
    indexSelf { self, key ->
        when(key.tojstring()) {
            "path" -> self.file.path.luaStr()
            "name" -> self.file.name.luaStr()
            "kind" -> luaValue("folder")
            else -> NIL
        }
    }
}

context(lua: LuaScriptEngine)
fun ModuleUserdataType() = LuaUserdataType<LuaFileModule> {
    functionSelfV("resolve_id") { self, args ->
        val module = self.module
        val idReference = args.arg1()
        val result = runCatching {
            when (idReference.type()) {
                LuaValue.TUSERDATA -> idReference.asEngineId()
                LuaValue.TSTRING -> module.resolveId(idReference.tojstring())
                else -> error("Invalid id type: ${idReference.typename()} (should be string or userdata)")
            }
        }
        LuaValue.varargsOf(
            result.getOrNull()?.let { lua.idLibrary.newInstance(it) } ?: NIL,
            result.exceptionOrNull()?.message?.luaStr() ?: NIL
        )
    }
    indexSelf { self, key ->
        when (key.tojstring()) {
            "document" -> self.document
            "dir" -> self.directory
            "namespace" -> self.module.namespace.value.luaStr()
            "allowed_namespaces" -> self.allowedNamespacesL
            "namespaces" -> self.namespacesL
            "depends" -> self.dependsL
            else -> NIL
        }
    }
}

context(lua: LuaScriptEngine)
fun ModulesTable(moduleManager: ModuleManager) = luaTable {
    "enabled"(
        moduleManager.modules.files.toList().toLuaList { (file, module) ->
            lua.moduleUserdataType.newInstance(
                LuaFileModule(
                    module,
                    file,
                    lua.moduleFileMetaTable.newInstance(
                        LuaModuleFile(file.config)
                    ),
                    lua.moduleFolderMetaTable.newInstance(
                        LuaModuleFolder(file.directory)
                    )
                )
            )
        }
    )
    "module"(lua.moduleUserdataType.metaTable)
}
