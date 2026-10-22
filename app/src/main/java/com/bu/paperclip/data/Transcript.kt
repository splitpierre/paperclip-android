package com.bu.paperclip.data

import com.bu.paperclip.net.AppJson
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

sealed class TEntry {
    abstract val ts: String?
    data class Assistant(override val ts: String?, val text: String, val thinking: Boolean = false) : TEntry()
    data class User(override val ts: String?, val text: String) : TEntry()
    data class ToolCall(override val ts: String?, val name: String, val id: String?, val input: String, val result: String? = null, val isError: Boolean = false, val done: Boolean = false) : TEntry()
    data class Init(override val ts: String?, val model: String) : TEntry()
    data class Result(override val ts: String?, val text: String, val inTok: Long, val outTok: Long, val cachedTok: Long, val costUsd: Double, val isError: Boolean, val errors: List<String>) : TEntry()
    data class Line(override val ts: String?, val stream: String, val text: String) : TEntry()
}

private fun JsonElement?.o(): JsonObject? = this as? JsonObject
private fun JsonObject.s(k: String): String = (this[k] as? JsonPrimitive)?.contentOrNull ?: ""
private fun JsonObject.n(k: String): Long = (this[k] as? JsonPrimitive)?.longOrNull ?: (this[k] as? JsonPrimitive)?.doubleOrNull?.toLong() ?: 0
private fun JsonObject.d(k: String): Double = (this[k] as? JsonPrimitive)?.doubleOrNull ?: 0.0
private fun pretty(e: JsonElement?): String = when (e) {
    null -> ""
    is JsonPrimitive -> e.contentOrNull ?: ""
    else -> runCatching { prettyJson.encodeToString(JsonElement.serializer(), e) }.getOrDefault(e.toString())
}
private val prettyJson = kotlinx.serialization.json.Json { prettyPrint = true }

/** Incrementally turns run log records (NDJSON {ts,stream,chunk,seq}) into transcript entries. */
class TranscriptBuilder(private val adapterType: String?) {
    val entries = ArrayList<TEntry>()
    var droppedEntries = 0L
        private set
    private val toolIndex = HashMap<String, TEntry.ToolCall>()
    private val stdoutBuf = StringBuilder()
    private val seenSeq = LinkedHashSet<Long>()
    private var partialRecord = ""

    /** Bound retained rendering history; the original log remains available on the server. */
    fun trim(maxEntries: Int): Boolean {
        if (entries.size <= maxEntries) return false
        val removed = entries.size - maxEntries
        entries.subList(0, removed).clear()
        droppedEntries += removed
        val retainedTools = entries.filterIsInstance<TEntry.ToolCall>().mapNotNull { it.id }.toHashSet()
        toolIndex.keys.retainAll(retainedTools)
        return true
    }

    /** Feed raw `content` from the /log endpoint (may end mid-line). */
    fun feedLogContent(content: String) {
        val data = partialRecord + content
        val lines = data.split('\n')
        partialRecord = lines.last()
        for (i in 0 until lines.size - 1) feedRecordLine(lines[i])
    }

    fun feedRecordLine(line: String) {
        if (line.isBlank()) return
        val rec = runCatching { AppJson.parseToJsonElement(line) }.getOrNull().o() ?: return
        feedRecord(rec.s("ts"), rec.s("stream"), rec.s("chunk"), (rec["seq"] as? JsonPrimitive)?.longOrNull)
    }

    fun feedRecord(ts: String?, stream: String, chunk: String, seq: Long?) {
        if (seq != null && !seenSeq.add(seq)) return
        while (seenSeq.size > 20_000) seenSeq.iterator().let { it.next(); it.remove() }
        when (stream) {
            "stdout" -> {
                stdoutBuf.append(chunk)
                var idx = stdoutBuf.indexOf("\n")
                while (idx >= 0) {
                    val l = stdoutBuf.substring(0, idx).trimEnd('\r')
                    stdoutBuf.delete(0, idx + 1)
                    if (l.isNotBlank()) parseStdout(l, ts)
                    idx = stdoutBuf.indexOf("\n")
                }
            }
            else -> chunk.trimEnd().takeIf { it.isNotEmpty() }?.let { add(TEntry.Line(ts, stream, it)) }
        }
    }

    private fun add(e: TEntry) {
        val last = entries.lastOrNull()
        if (e is TEntry.Assistant && last is TEntry.Assistant && last.thinking == e.thinking && pendingDelta) {
            entries[entries.size - 1] = last.copy(text = last.text + e.text)
            return
        }
        if (e is TEntry.Line && last is TEntry.Line && last.stream == e.stream && e.stream == "stdout") {
            entries[entries.size - 1] = last.copy(text = last.text + "\n" + e.text)
            return
        }
        entries.add(e)
    }

    private var pendingDelta = false

    private fun toolCall(ts: String?, name: String, id: String?, input: JsonElement?) {
        val existing = id?.let { toolIndex[it] }
        if (existing != null) return
        val tc = TEntry.ToolCall(ts, name, id, pretty(input))
        if (id != null) toolIndex[id] = tc
        pendingDelta = false
        add(tc)
    }

    private fun toolResult(ts: String?, id: String?, name: String?, content: String, isError: Boolean) {
        val tc = id?.let { toolIndex[it] }
        if (tc != null) {
            val idx = entries.indexOfFirst { it === tc }
            val updated = tc.copy(result = content, isError = isError, done = true)
            if (idx >= 0) entries[idx] = updated
            toolIndex[id] = updated
        } else {
            add(TEntry.ToolCall(ts, name ?: "tool", id, "", content, isError, true))
        }
        pendingDelta = false
    }

    private fun parseStdout(line: String, ts: String?) {
        val j = runCatching { AppJson.parseToJsonElement(line) }.getOrNull().o()
        if (j == null) { pendingDelta = false; add(TEntry.Line(ts, "stdout", line)); return }
        val type = j.s("type")
        when {
            type.startsWith("acpx.") -> parseAcpx(j, type, ts)
            j.containsKey("event") && (j.containsKey("step_update") || j.containsKey("init")) -> parseAntigravity(j, ts)
            adapterType == "codex_local" || type == "thread.started" || type.startsWith("item.") || type.startsWith("turn.") -> parseCodex(j, type, ts, line)
            else -> parseClaude(j, type, ts, line)
        }
    }

    private fun parseClaude(j: JsonObject, type: String, ts: String?, line: String) {
        pendingDelta = false
        when (type) {
            "system" -> if (j.s("subtype") == "init") add(TEntry.Init(ts, j.s("model").ifEmpty { "unknown" }))
            "assistant" -> {
                val content = j["message"].o()?.get("content") as? JsonArray ?: return
                content.forEach { b ->
                    val bo = b.o() ?: return@forEach
                    when (bo.s("type")) {
                        "text" -> bo.s("text").takeIf { it.isNotEmpty() }?.let { add(TEntry.Assistant(ts, it)) }
                        "thinking" -> bo.s("thinking").takeIf { it.isNotEmpty() }?.let { add(TEntry.Assistant(ts, it, thinking = true)) }
                        "tool_use" -> toolCall(ts, bo.s("name").ifEmpty { "tool" }, bo.s("id").ifEmpty { null }, bo["input"])
                    }
                }
            }
            "user" -> {
                val content = j["message"].o()?.get("content") as? JsonArray ?: return
                content.forEach { b ->
                    val bo = b.o() ?: return@forEach
                    when (bo.s("type")) {
                        "text" -> bo.s("text").takeIf { it.isNotEmpty() }?.let { add(TEntry.User(ts, it)) }
                        "tool_result" -> {
                            val c = bo["content"]
                            val text = when (c) {
                                is JsonPrimitive -> c.contentOrNull ?: ""
                                is JsonArray -> c.mapNotNull { p -> p.o()?.s("text")?.takeIf { it.isNotEmpty() } }.joinToString("\n")
                                else -> ""
                            }
                            toolResult(ts, bo.s("tool_use_id").ifEmpty { null }, null, text, (bo["is_error"] as? JsonPrimitive)?.contentOrNull == "true")
                        }
                    }
                }
            }
            "result" -> {
                val u = j["usage"].o() ?: JsonObject(emptyMap())
                add(TEntry.Result(ts, j.s("result"), u.n("input_tokens"), u.n("output_tokens"), u.n("cache_read_input_tokens"), j.d("total_cost_usd"),
                    j.s("is_error") == "true", (j["errors"] as? JsonArray)?.map { pretty(it) } ?: emptyList()))
            }
            // Bookkeeping events (rate limits, hooks, stream deltas) stay in the raw log only.
            else -> if (type.isEmpty()) add(TEntry.Line(ts, "stdout", line))
        }
    }

    private fun parseCodex(j: JsonObject, type: String, ts: String?, line: String) {
        pendingDelta = false
        when (type) {
            "thread.started" -> add(TEntry.Init(ts, j.s("model").ifEmpty { "codex" }))
            "turn.started" -> {}
            "item.started", "item.completed" -> {
                val item = j["item"].o() ?: return
                val started = type == "item.started"
                val id = item.s("id").ifEmpty { null }
                when (item.s("type")) {
                    "agent_message" -> item.s("text").takeIf { it.isNotEmpty() }?.let { add(TEntry.Assistant(ts, it)) }
                    "reasoning" -> item.s("text").takeIf { it.isNotEmpty() }?.let { add(TEntry.Assistant(ts, it, thinking = true)) }
                    "command_execution" -> if (started) toolCall(ts, "command", id, JsonPrimitive(item.s("command"))) else {
                        val exit = (item["exit_code"] as? JsonPrimitive)?.longOrNull
                        val out = buildString {
                            if (exit != null) append("exit $exit\n")
                            append(item.s("aggregated_output").trimEnd())
                        }
                        if (id != null && toolIndex[id] == null) toolCall(ts, "command", id, JsonPrimitive(item.s("command")))
                        toolResult(ts, id, "command", out, (exit != null && exit != 0L) || item.s("status") in setOf("failed", "error"))
                    }
                    "file_change" -> if (!started) {
                        val ch = (item["changes"] as? JsonArray)?.mapNotNull { it.o() }?.joinToString(", ") { "${it.s("kind")} ${it.s("path")}" }
                        add(TEntry.Line(ts, "system", "file changes: ${ch ?: ""}"))
                    }
                    "mcp_tool_call", "tool_use" -> if (started) toolCall(ts, item.s("name").ifEmpty { item.s("tool") }.ifEmpty { "tool" }, id, item["input"] ?: item["arguments"])
                        else toolResult(ts, id, item.s("name"), pretty(item["result"] ?: item["output"] ?: item["content"] ?: item["error"]), item.s("status") in setOf("failed", "error"))
                    "error" -> if (!started) add(TEntry.Line(ts, "stderr", item.s("message")))
                    else -> {}
                }
            }
            "turn.completed", "turn.failed" -> {
                val u = j["usage"].o() ?: JsonObject(emptyMap())
                add(TEntry.Result(ts, j.s("result"), u.n("input_tokens"), u.n("output_tokens"), u.n("cached_input_tokens"), j.d("total_cost_usd"),
                    type == "turn.failed", listOfNotNull(pretty(j["error"]).takeIf { it.isNotEmpty() })))
            }
            "error" -> add(TEntry.Line(ts, "stderr", j.s("message").ifEmpty { line }))
            else -> if (type.isEmpty()) add(TEntry.Line(ts, "stdout", line))
        }
    }

    private fun parseAcpx(j: JsonObject, type: String, ts: String?) {
        when (type) {
            "acpx.session" -> { pendingDelta = false; add(TEntry.Init(ts, j.s("agent").ifEmpty { "acpx" })) }
            "acpx.text_delta" -> {
                val text = j.s("text"); if (text.isEmpty()) return
                val ch = j.s("channel").ifEmpty { j.s("stream") }
                val thinking = ch == "thought" || ch == "thinking"
                val last = entries.lastOrNull()
                if (pendingDelta && last is TEntry.Assistant && last.thinking == thinking) {
                    entries[entries.size - 1] = last.copy(text = last.text + text)
                } else {
                    pendingDelta = false
                    entries.add(TEntry.Assistant(ts, text, thinking))
                }
                pendingDelta = true
            }
            "acpx.tool_call" -> {
                val id = j.s("toolCallId").ifEmpty { j.s("id") }.ifEmpty { j.s("toolUseId") }.ifEmpty { null }
                val status = j.s("status")
                toolCall(ts, j.s("name").ifEmpty { j.s("title") }.ifEmpty { "tool" }, id, j["input"] ?: JsonPrimitive(j.s("text")))
                if (status == "completed" || status == "failed" || status == "cancelled") toolResult(ts, id, j.s("name"), j.s("text").ifEmpty { status }, status != "completed")
            }
            "acpx.tool_result" -> {
                val id = j.s("toolCallId").ifEmpty { j.s("id") }.ifEmpty { j.s("toolUseId") }.ifEmpty { null }
                toolResult(ts, id, j.s("name"), pretty(j["content"] ?: j["output"] ?: j["error"]), j.s("isError") == "true" || j.containsKey("error"))
            }
            "acpx.status" -> { pendingDelta = false; j.s("text").ifEmpty { j.s("tag") }.takeIf { it.isNotEmpty() }?.let { add(TEntry.Line(ts, "system", it)) } }
            "acpx.result" -> { pendingDelta = false
                add(TEntry.Result(ts, j.s("summary").ifEmpty { j.s("stopReason") }, j.n("inputTokens"), j.n("outputTokens"), j.n("cachedTokens"), j.d("costUsd"), false, emptyList())) }
            "acpx.error" -> { pendingDelta = false; add(TEntry.Line(ts, "stderr", j.s("message"))) }
            else -> { pendingDelta = false; j.s("message").takeIf { it.isNotEmpty() }?.let { add(TEntry.Line(ts, "system", it)) } }
        }
    }

    private var agUsageIn = 0L
    private var agUsageOut = 0L

    private fun parseAntigravity(j: JsonObject, ts: String?) {
        pendingDelta = false
        val ev = j.s("event")
        if (ev == "init") {
            add(TEntry.Init(ts, j["init"].o()?.s("model")?.ifEmpty { null } ?: "antigravity")); return
        }
        val su = j["step_update"].o() ?: return
        val id = "ag-" + su.s("step_index")
        val state = su.s("state")
        when (su.s("step_type")) {
            "tool" -> {
                val info = su["tool_info"].o()
                val name = su.s("tool_name").ifEmpty { info?.s("name") ?: "tool" }
                if (state == "ACTIVE") toolCall(ts, name, id, info?.get("parameters"))
                else if (state == "DONE" || state == "ERROR" || state == "FAILED") {
                    if (toolIndex[id] == null) toolCall(ts, name, id, info?.get("parameters"))
                    toolResult(ts, id, name, pretty(info?.get("output") ?: info?.get("error")), state != "DONE")
                }
            }
            "agent_response", "planner_response" -> {
                val text = su.s("text").ifEmpty { su.s("response") }.ifEmpty { su["agent_response"].o()?.s("text") ?: "" }
                if (text.isNotEmpty() && state == "DONE") add(TEntry.Assistant(ts, text))
                su["usage"].o()?.let { u -> agUsageIn += u.n("input_tokens"); agUsageOut += u.n("output_tokens") }
            }
            "user_input" -> {}
            else -> if (state == "DONE") add(TEntry.Line(ts, "system", "${su.s("step_type")} done"))
        }
    }

    /** Antigravity reports usage per step; surfaced once the run ends. */
    fun antigravityUsage(): Pair<Long, Long>? = if (agUsageIn + agUsageOut > 0) agUsageIn to agUsageOut else null
}
