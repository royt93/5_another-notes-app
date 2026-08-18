package com.maltaisn.notes

/**
 * For checks only performed in debug mode. This file only compiles into debug build types
 * (`app/src/debug/kotlin`) — the release counterpart at
 * `app/src/release/kotlin/com/maltaisn/notes/DebugExtensions.kt` no-ops both functions instead.
 * A failing check should be handled correctly in release mode.
 */
inline fun debugCheck(value: Boolean, message: () -> String = { "Check failed" }) {
    check(value, message)
}

inline fun debugRequire(value: Boolean, message: () -> String = { "Failed requirement" }) {
    require(value, message)
}
