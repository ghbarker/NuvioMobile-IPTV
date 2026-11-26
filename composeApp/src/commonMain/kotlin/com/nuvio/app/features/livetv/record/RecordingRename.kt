package com.nuvio.app.features.livetv.record

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

/** Editing a saved title never changes the recording's identity or the video it points to. */
internal object RecordingRename {
    const val MAX_TITLE_LENGTH = 200
    const val CHANGED = "This recording changed. Reopen its options and try again"
    const val COPYING = "A recording is being copied to the network folder. Try again after it finishes"
    const val FOLDER_CHANGED = "The network folder changed. Reopen this recording and try again"

    fun title(input: String): String? = input.trim().takeIf {
        it.isNotEmpty() && it.length <= MAX_TITLE_LENGTH && it.none { c -> c.code < 32 || c.code in 127..159 }
    }

    fun sameRecording(current: LiveTvRecording, expected: LiveTvRecording): Boolean =
        current.id == expected.id && current.dir == expected.dir &&
            current.startedAtMs == expected.startedAtMs && current.title == expected.title &&
            current.status == expected.status && !current.isRecording

    /** Patch the original object so TV-specific and future metadata survives a phone edit. */
    fun info(text: String, expected: LiveTvRecording, input: String): String {
        val title = requireNotNull(title(input)) { "Enter a title of 1–200 characters without line breaks" }
        val raw = try { Json.parseToJsonElement(text).jsonObject }
            catch (_: Exception) { error("The recording's details couldn't be read") }
        check((raw["status"] as? JsonPrimitive)?.content in setOf("DONE", "FAILED")) { CHANGED }
        val current = LiveTvRecording.fromJson(raw, expected.dir)
        check(current != null && sameRecording(current, expected)) { CHANGED }
        return JsonObject(raw + ("title" to JsonPrimitive(title))).toString()
    }
}
