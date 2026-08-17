import com.mckimquyen.notes.BuildConfig

/**
 * For checks only performed when [BuildConfig.ENABLE_DEBUG_FEATURES] is true.
 * A failing check should be handled correctly in release mode.
 *
 * This file lives under `src/main` (compiled into every build type), so the
 * debug/release split is enforced at runtime here rather than by source set —
 * see FIX-C04 in doc/task/todo/FIX.md.
 */
inline fun debugCheck(value: Boolean, message: () -> String = { "Check failed" }) {
    if (BuildConfig.ENABLE_DEBUG_FEATURES) {
        check(value, message)
    }
}

inline fun debugRequire(value: Boolean, message: () -> String = { "Failed requirement" }) {
    if (BuildConfig.ENABLE_DEBUG_FEATURES) {
        require(value, message)
    }
}
