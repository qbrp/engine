package org.lain.engine.script.compilation

sealed interface CompilationOutcome {
    val report: CompilationReport

    data class Success(
        val build: Build,
        override val report: CompilationReport
    ) : CompilationOutcome {
        fun log() {
            build.log()
        }
    }

    data class Failure(
        override val report: CompilationReport
    ) : CompilationOutcome

    fun successOrThrow() = when(this) {
        is Failure -> throw CompilationFailedException(report)
        is Success -> build
    }
}