package org.lain.engine.script.lua.compilation

import org.lain.engine.script.SystemPhase
import org.lain.engine.script.TickPhases
import org.lain.engine.script.compilation.CompilationContext
import org.lain.engine.script.compilation.CompilationDiagnosticSeverity
import org.lain.engine.script.compilation.CompilationDiagnosticTarget
import org.lain.engine.script.compilation.CompilationPhase
import org.lain.engine.script.compilation.PhaseStepDraft
import org.lain.engine.script.compilation.SymbolKind
import org.lain.engine.script.compilation.SystemPhaseDraft
import org.lain.engine.script.compilation.TickPhasesDraft
import org.lain.engine.script.compilation.toDiagnostic
import org.lain.engine.script.lua.library.resolveIdReference
import org.lain.engine.script.lua.nullable
import org.lain.engine.script.lua.toList
import org.lain.engine.script.toScriptSystemId
import org.luaj.vm2.LuaTable

context(context: CompilationContext)
fun compileTickPhasesDraft(buildL: LuaTable): TickPhasesDraft {
    return try {
        val phasesL = buildL["phases"]?.nullable()?.checktable()
        TickPhasesDraft(
            phasesL?.get("base")?.nullable()?.checktable()?.toPhaseDraft(),
            phasesL?.get("verb_lookup")?.nullable()?.checktable()?.toPhaseDraft()
        )
    } catch (e: Exception) {
        context.exceptions.abort(
            e.toDiagnostic(
                CompilationPhase.COMPILATION,
                severity = CompilationDiagnosticSeverity.FATAL,
                target = CompilationDiagnosticTarget(SymbolKind.PHASE, "root_phase")
            )
        )
    }
}

private fun LuaTable.toPhaseDraft(): SystemPhaseDraft {

    return SystemPhaseDraft(
        get("name").tojstring(),
        get("steps").checktable().toList { stepL ->
            val type = stepL["type"].tojstring()
            when(type) {
                "phase" -> PhaseStepDraft.Phase(stepL["phase"].checktable().toPhaseDraft())
                "system" -> PhaseStepDraft.System(stepL["system"].resolveIdReference().toScriptSystemId())
                else -> error("Unknown phase step draft type: $type (supports `phase` and `system`)")
            }
        },
    )
}