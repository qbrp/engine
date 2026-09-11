package org.lain.engine.script.lua.compilation

import org.lain.engine.script.NamespaceId
import org.lain.engine.script.compilation.CompilationContext
import org.lain.engine.script.compilation.CompilationDiagnostic
import org.lain.engine.script.compilation.CompilationDiagnosticLocation
import org.lain.engine.script.compilation.CompilationDiagnosticSeverity
import org.lain.engine.script.compilation.CompilationDiagnosticTarget
import org.lain.engine.script.compilation.CompilationPhase
import org.lain.engine.script.compilation.CompilationReportBuilder
import org.lain.engine.script.compilation.SymbolKind
import org.lain.engine.script.compilation.reportInvalidNamespace
import org.lain.engine.script.lua.LuaScriptEngine
import org.lain.engine.script.lua.LuaUserdataType
import org.lain.engine.script.lua.NIL
import org.lain.engine.script.lua.library.asEngineId
import org.lain.engine.script.lua.luaTable
import org.lain.engine.script.lua.nullable
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue

private fun LuaValue.toSymbolKind() = SymbolKind(checkjstring())

private fun LuaTable.toReportLocation() = CompilationDiagnosticLocation(
    source = get("source").checkjstring(),
    line = get("line").nullable()?.checkint(),
    column = get("column").nullable()?.checkint(),
    path = get("path").nullable()?.checkjstring(),
)

private fun LuaTable.toReportTarget() = CompilationDiagnosticTarget(
    kind = get("kind").toSymbolKind(),
    localId = get("local_id").checkjstring(),
)

private fun LuaTable.toCompilationDiagnostic() = CompilationDiagnostic(
    severity = CompilationDiagnosticSeverity.valueOf(
        get("severity").checkjstring().uppercase()
    ),
    message = get("message").checkjstring(),
    phase = CompilationPhase.valueOf(
        get("phase").checkjstring().uppercase()
    ),
    namespace = get("namespace").nullable()
        ?.checkjstring()
        ?.let(::NamespaceId),
    location = get("location").nullable()?.checktable()?.toReportLocation(),
    target = get("target").nullable()?.checktable()?.toReportTarget(),
)

fun ReportsCollectorUserdataType() = LuaUserdataType<CompilationReportBuilder> {
    functionSelf2("report") { self, report ->
        self.report(report.checktable().toCompilationDiagnostic())
        NIL
    }
    functionSelf2("abort") { self, report ->
        val fatalDiagnostic = report.checktable().toCompilationDiagnostic()
            .copy(severity = CompilationDiagnosticSeverity.FATAL) // защита от дураков
        self.abort(fatalDiagnostic)
    }
    functionSelf4("report_invalid_namespace") { self, namespace, id, kind ->
        self.reportInvalidNamespace(
            NamespaceId(namespace.tojstring()),
            id.asEngineId(),
            kind.toSymbolKind()
        )
        NIL
    }
    indexSelf { self, key -> NIL }
}

context(lua: LuaScriptEngine)
fun CompilationContextTable(context: CompilationContext) = luaTable {
    "reports"(lua.reportsCollectorUserdataType.newInstance(context.exceptions))
}
