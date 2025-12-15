package com.example.wristtype.presentation

import android.content.Context
import org.json.JSONObject
import java.io.File

class StudyLogger(private val context: Context) {

    private var file: File? = null

    fun startSession(participantId: String) {
        val safeId = participantId.ifBlank { "anon" }.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        val name = "wristflick_session_${safeId}_${System.currentTimeMillis()}.jsonl"
        file = File(context.filesDir, name)
        log("session_start", mapOf("participantId" to safeId))
    }

    fun log(event: String, fields: Map<String, Any?> = emptyMap()) {
        val f = file ?: return
        val obj = JSONObject()
        obj.put("ts_ms", System.currentTimeMillis())
        obj.put("event", event)
        for ((k, v) in fields) obj.put(k, v)
        f.appendText(obj.toString() + "\n")
    }

    fun filename(): String? = file?.name
}
