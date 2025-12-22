package org.lain.engine.util

fun String.replaceLazy(oldValue: String, newValue: () -> String): String {
    var out = this
    while (out.contains(oldValue)) {
        out = out.replaceFirst(oldValue, newValue())
    }
    return out
}